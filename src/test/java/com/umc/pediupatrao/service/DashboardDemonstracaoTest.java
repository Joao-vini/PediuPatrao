package com.umc.pediupatrao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;

/** Fixture opt-in, jamais componente de produção. Recusa qualquer banco já utilizado. */
@EnabledIfEnvironmentVariable(named = "PP_DEMO_URI", matches = ".+")
class DashboardDemonstracaoTest {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String DE = "2026-10-01", ATE = "2026-10-07";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private PedidoRepository pedidos;
    private PedidoService service;
    private Cliente cliente;
    private String senha;
    private final Authentication gerente = auth("demo-gerente", "GERENTE");
    private static Authentication auth(String nome, String perfil) {
        return UsernamePasswordAuthenticationToken.authenticated(nome, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
    }

    @Test void homologarCenarioSinteticoEmBancoNovo() throws Exception {
        ConnectionString cs = new ConnectionString(System.getenv("PP_DEMO_URI"));
        assertEquals(List.of("127.0.0.1:27028"), cs.getHosts());
        assertNotNull(cs.getDatabase());
        assertTrue(cs.getDatabase().matches("pediupatrao_test_demo_[a-f0-9]{32}"));
        assertEquals("codexDashboard", cs.getRequiredReplicaSetName());
        try (var client = MongoClients.create(System.getenv("PP_DEMO_URI"))) {
            assertEquals("codexDashboard", client.getDatabase("admin").runCommand(new org.bson.Document("hello",1)).getString("setName"));
            assertFalse(client.listDatabaseNames().into(new ArrayList<>()).contains(cs.getDatabase()), "Não reutilizar banco existente");
            var factory = new SimpleMongoClientDatabaseFactory(client, cs.getDatabase());
            MongoTemplate mongo = new MongoTemplate(factory);
            for (Class<?> type : List.of(Pedido.class, Auditoria.class, Cliente.class, Produto.class, Usuario.class)) mongo.createCollection(type);
            var repositories = new MongoRepositoryFactory(mongo);
            pedidos = repositories.getRepository(PedidoRepository.class);
            var clientes = repositories.getRepository(ClienteRepository.class);
            var produtos = repositories.getRepository(ProdutoRepository.class);
            var usuarios = repositories.getRepository(UsuarioRepository.class);
            var registros = repositories.getRepository(AuditoriaRepository.class);
            var auditoria = new AuditoriaService(registros, mongo);
            var encoder = new BCryptPasswordEncoder(); senha = UUID.randomUUID().toString();
            Usuario operador = new Usuario(); operador.setUsername("demo-gerente"); operador.setPassword(senha); operador.setRole("GERENTE");
            new UsuarioService(usuarios, encoder, auditoria).salvarUsuario(operador, auth("fixture", "ADMIN"));
            ClienteService clienteService = new ClienteService();
            ReflectionTestUtils.setField(clienteService, "clienteRepository", clientes);
            ReflectionTestUtils.setField(clienteService, "auditoriaService", auditoria);
            cliente = new Cliente(); cliente.setNome("CLIENTE SINTÉTICO — NÃO REAL"); cliente.setEndereco("Endereço de teste sem destinatário real");
            cliente = clienteService.novoCliente(cliente, auth("fixture-atendente", "ATENDENTE"));
            ProdutoService produtoService = new ProdutoService();
            ReflectionTestUtils.setField(produtoService, "produtoRepository", produtos);
            ReflectionTestUtils.setField(produtoService, "pedidoRepository", pedidos);
            Produto a = produto(produtoService, "TESTE — Pizza A", "Pizza", 40);
            Produto b = produto(produtoService, "TESTE — Bebida B", "Bebida", 10);
            Produto c = produto(produtoService, "TESTE — Pizza C", "Pizza", 60);
            Produto d = produto(produtoService, "TESTE — Porção D", "Porção", 15);
            var proxy = new ProxyFactory(new PedidoService(pedidos, clientes, produtos, usuarios, auditoria, encoder));
            proxy.addAdvice(new TransactionInterceptor(new MongoTransactionManager(factory), new AnnotationTransactionAttributeSource()));
            service = (PedidoService) proxy.getProxy();

            criar("P1", 1, Pedido.TipoPedido.RETIRADA, "RECEBIDO", false, a, 2);
            criar("P2", 2, Pedido.TipoPedido.ENTREGA, "EM_PREPARO", true, a, 1, b, 2);
            criar("P3", 4, Pedido.TipoPedido.BALCAO, "FINALIZADO", false, c, 1, b, 1);
            criar("P4", 4, Pedido.TipoPedido.RETIRADA, "CANCELADO", false, a, 3);
            criar("P5", 6, Pedido.TipoPedido.ENTREGA, "SAIU_PARA_ENTREGA", false, c, 2);
            criar("P6", 7, Pedido.TipoPedido.BALCAO, "PRONTO", false, b, 3);
            c.setPreco(70.0); produtoService.salvar(c); // Preços antigos dos itens não devem mudar.
            criar("P7", 7, Pedido.TipoPedido.RETIRADA, "FINALIZADO", false, c, 1, b, 1);
            criar("P8", 3, Pedido.TipoPedido.RETIRADA, "RECEBIDO", false, d, 3);
            // Exceção deliberada da fixture: simular documento legado incompleto,
            // que o fluxo normal corretamente não permite cadastrar.
            Pedido legado = new Pedido(); legado.setObservacaoGeral("P9 — TESTE LEGADO SINTÉTICO SEM VALOR");
            legado.setDataHora(data(5)); pedidos.save(legado);

            List<Pedido> persistidos = pedidos.findAll(); assertEquals(9, persistidos.size());
            DashboardService dashboard = new DashboardService(mongo);
            var filtro = dashboard.filtro(DE, ATE, null, null);
            var resumo = dashboard.consultar(filtro);
            assertEquals(json.valueToTree(indicadores(persistidos)), json.valueToTree(resumo.indicadores()));
            assertEquals(new BigDecimal("488.50"), resumo.indicadores().faturamento());
            assertEquals(new BigDecimal("69.79"), resumo.indicadores().ticketMedio());
            assertEquals(new BigDecimal("6.50"), resumo.indicadores().descontos());
            assertEquals(List.of("80.00","58.50","45.00","70.00","0.00","125.00","110.00"), resumo.vendas().stream().map(v -> v.faturamento().toPlainString()).toList());
            assertEquals(List.of(7L,4L,3L,3L), resumo.produtos().stream().map(DashboardService.ProdutoVendido::quantidade).toList());
            assertEquals(List.of(new BigDecimal("70.00"),new BigDecimal("250.00")), resumo.produtos().subList(0,2).stream().map(DashboardService.ProdutoVendido::valorBruto).toList());
            assertTrue(resumo.produtos().get(2).produtoId().compareTo(resumo.produtos().get(3).produtoId()) < 0);
            assertEquals(9, resumo.situacao().stream().mapToLong(DashboardService.Situacao::quantidade).sum());
            assertEquals(9, resumo.tipos().stream().mapToLong(DashboardService.DistribuicaoTipo::quantidade).sum());
            assertEquals(Map.of("RECEBIDO",2L,"EM_PREPARO",1L,"FINALIZADO",2L,"PRONTO",1L,
                    "CANCELADO",1L,"SAIU_PARA_ENTREGA",1L,"SEM_STATUS",1L), resumo.situacao().stream()
                    .collect(Collectors.toMap(DashboardService.Situacao::status,DashboardService.Situacao::quantidade)));
            assertEquals(Map.of("RETIRADA",4L,"ENTREGA",2L,"BALCAO",2L,"SEM_TIPO",1L), resumo.tipos().stream()
                    .collect(Collectors.toMap(DashboardService.DistribuicaoTipo::tipo,DashboardService.DistribuicaoTipo::quantidade)));
            assertEquals(Map.of(a.getId(),new BigDecimal("120.00"),d.getId(),new BigDecimal("45.00")), resumo.produtos().subList(2,4).stream()
                    .collect(Collectors.toMap(DashboardService.ProdutoVendido::produtoId,DashboardService.ProdutoVendido::valorBruto)));
            assertEquals(8, registros.findAll().stream().filter(e -> "PEDIDO_CRIADO".equals(e.getOperacao())).count());
            Map<String,Object> export = new LinkedHashMap<>();
            export.put("aviso", "AMBIENTE DE DEMONSTRAÇÃO — DADOS DE TESTE");
            export.put("inicio", DE); export.put("fim", ATE);
            export.put("resumo", resumo);
            export.put("casos", casos(dashboard, persistidos));
            export.put("pedidos", persistidos.stream().sorted(Comparator.comparing(Pedido::getDataHora)).map(p -> {
                Map<String,Object> row = new LinkedHashMap<>(); row.put("id",p.getId()); row.put("codigo", p.getObservacaoGeral()); row.put("data",p.getDataHora().atZone(FUSO).toLocalDate().toString());
                row.put("tipo",p.getTipoPedido()); row.put("status",p.getStatus()); row.put("total",p.getValorTotal()); row.put("desconto",p.getValorDesconto()); return row;
            }).toList());
            Path output = Path.of(System.getenv("PP_DEMO_EXPECTED")).toAbsolutePath().normalize();
            assertTrue(output.startsWith(Path.of("target").toAbsolutePath().normalize()));
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), export);
            System.out.println("DEMONSTRAÇÃO VALIDADA: 9 registros sintéticos; faturamento 488.50; ticket 69.79; desconto 6.50; sem dados reais.");
        }
    }

    private Produto produto(ProdutoService produtos, String nome, String tipo, double preco) {
        Produto p = new Produto(); p.setNome(nome); p.setTipo(tipo); p.setPreco(preco); p.setAtivo(true); return produtos.novoProduto(p);
    }
    private Instant data(int dia) { return LocalDate.of(2026,10,dia).atTime(12,0).atZone(FUSO).toInstant(); }
    private void criar(String codigo, int dia, Pedido.TipoPedido tipo, String status, boolean desconto, Object... itens) {
        Pedido p = new Pedido(); p.setClienteId(cliente.getId()); p.setTipoPedido(tipo); p.setFormaPagamento(Pedido.FormaPagamento.PIX);
        p.setTaxaEntrega(tipo == Pedido.TipoPedido.ENTREGA ? new BigDecimal("5") : BigDecimal.ZERO);
        p.setObservacaoGeral(codigo + " — DADOS SINTÉTICOS DE TESTE");
        List<Pedido.ItemPedido> lista = new ArrayList<>();
        for (int i=0;i<itens.length;i+=2) { var item = new Pedido.ItemPedido(); item.setProdutoId(((Produto)itens[i]).getId()); item.setQuantidade((Integer)itens[i+1]); lista.add(item); }
        p.setItens(lista);
        if(desconto) { p.setTipoDesconto(Pedido.TipoDesconto.PERCENTUAL); p.setDescontoInformado(BigDecimal.TEN); }
        p = service.criarPedido(p, "demo-gerente", senha, gerente);
        if ("CANCELADO".equals(status)) service.cancelar(p.getId(), "CANCELAMENTO SINTÉTICO DE TESTE", "demo-gerente", senha, gerente);
        else while(!status.equals(p.getStatus())) { p = service.atualizarStatus(p.getId(), service.proximoStatus(p), gerente); }
        // Datas históricas somente nesta fixture: o fluxo normal atribui Instant.now.
        p = pedidos.findById(p.getId()).orElseThrow(); p.setDataHora(data(dia)); pedidos.save(p);
    }
    private Map<String,Object> indicadores(List<Pedido> rows) {
        var validos = rows.stream().filter(p -> !"CANCELADO".equals(p.getStatus()) && p.getValorTotal()!=null && p.getValorTotal().signum()>=0).toList();
        BigDecimal soma = validos.stream().map(Pedido::getValorTotal).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
        BigDecimal descontos = validos.stream().map(Pedido::getValorDesconto).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
        long cancelados = rows.stream().filter(p -> "CANCELADO".equals(p.getStatus())).count();
        return Map.of("faturamento",soma,"pedidos",(long)rows.size(),"pedidosValidos",(long)validos.size(),"cancelados",cancelados,
                "ticketMedio",validos.isEmpty()?new BigDecimal("0.00"):soma.divide(BigDecimal.valueOf(validos.size()),2,RoundingMode.HALF_UP),
                "descontos",descontos,"semValor",(long)rows.size()-validos.size()-cancelados);
    }
    private List<Map<String,Object>> casos(DashboardService dashboard,List<Pedido> rows) {
        List<Map<String,Object>> casos = new ArrayList<>();
        for (String[] c : List.of(new String[]{DE,ATE,"",""},new String[]{DE,ATE,"","RETIRADA"},new String[]{DE,ATE,"","ENTREGA"},
                new String[]{DE,ATE,"","BALCAO"},new String[]{DE,ATE,"CANCELADO",""},new String[]{"2026-10-02","2026-10-04","",""},
                new String[]{DE,"2026-10-04","EM_PREPARO","ENTREGA"},new String[]{"2026-09-01","2026-09-07","",""},new String[]{DE,ATE,"SEM_STATUS",""})) {
            var filtro = dashboard.filtro(c[0],c[1],c[2],c[3]);
            var selecionados = rows.stream().filter(p -> { LocalDate date=p.getDataHora().atZone(FUSO).toLocalDate();
                return !date.isBefore(filtro.inicio()) && !date.isAfter(filtro.fim())
                        && (c[2].isEmpty() || c[2].equals(p.getStatus()==null?"SEM_STATUS":p.getStatus()))
                        && (c[3].isEmpty() || (p.getTipoPedido()!=null && c[3].equals(p.getTipoPedido().name()))); }).toList();
            Map<String,Object> esperado=indicadores(selecionados);
            assertEquals(json.valueToTree(esperado), json.valueToTree(dashboard.consultar(filtro).indicadores()));
            casos.add(Map.of("parametros",Map.of("inicio",c[0],"fim",c[1],"status",c[2],"tipo",c[3]),"indicadores",esperado));
        }
        return casos;
    }
}
