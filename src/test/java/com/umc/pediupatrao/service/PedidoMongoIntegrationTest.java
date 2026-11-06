package com.umc.pediupatrao.service;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in: exclusivamente Mongo local descartável, nunca lê application.properties. */
@EnabledIfEnvironmentVariable(named = "PP_TEST_MONGO_URI", matches = ".+")
class PedidoMongoIntegrationTest {
    @Test void rollbackRealDePedidoEAuditoriaEmReplicaSetDescartavel() {
        String uri = System.getenv("PP_TEST_MONGO_URI");
        ConnectionString cs = new ConnectionString(uri);
        assertNotNull(cs.getDatabase());
        assertTrue(cs.getDatabase().startsWith("pediupatrao_test_"), "Banco deve ter prefixo pediupatrao_test_");
        assertEquals(List.of("127.0.0.1:27028"), cs.getHosts(), "Somente o servidor descartável dedicado é permitido");
        assertTrue(cs.getCredential() == null, "Não utilizar credenciais reais no teste descartável");
        try (var client = MongoClients.create(uri)) {
            var hello = client.getDatabase("admin").runCommand(new org.bson.Document("hello", 1));
            if (!hello.containsKey("setName") && "codexDashboard".equals(System.getenv("PP_TEST_MONGO_INIT"))) {
                assertEquals(List.of("127.0.0.1:27028"), cs.getHosts(), "Inicialização restrita ao servidor descartável dedicado");
                client.getDatabase("admin").runCommand(new org.bson.Document("replSetInitiate",
                        new org.bson.Document("_id", "codexDashboard").append("members", List.of(
                                new org.bson.Document("_id", 0).append("host", "127.0.0.1:27028")))));
                long limite = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
                do {
                    hello = client.getDatabase("admin").runCommand(new org.bson.Document("hello", 1));
                    if (Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))) break;
                    try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                } while (System.nanoTime() < limite);
            }
            assertEquals("codexDashboard",hello.getString("setName"),"O teste exige o replica set descartável dedicado");
            // Banco novo por execução; não altera nem remove registros preexistentes.
            String db = cs.getDatabase() + "_" + UUID.randomUUID().toString().replace("-", "");
            var factory = new SimpleMongoClientDatabaseFactory(client, db);
            MongoTemplate mongo = new MongoTemplate(factory);
            mongo.createCollection(Pedido.class); mongo.createCollection(Auditoria.class);
            var repositories = new MongoRepositoryFactory(mongo);
            // O repositório já é um proxy Spring. Delegar evita espionar esse proxy
            // e permite injetar falhas sem interferir no resolver de mocks Spring.
            PedidoRepository pedidos = mock(PedidoRepository.class,
                    org.mockito.AdditionalAnswers.delegatesTo(repositories.getRepository(PedidoRepository.class)));
            AuditoriaRepository registros = repositories.getRepository(AuditoriaRepository.class);
            AuditoriaService auditoria = spy(new AuditoriaService(registros, mongo));
            Pedido p = new Pedido(); p.setStatus("RECEBIDO"); p.setValorTotal(new BigDecimal("100"));p.setDataHora(java.time.Instant.now());
            p.setSubtotalProdutos(new BigDecimal("100")); p = pedidos.save(p);
            String id = p.getId();
            UsuarioRepository usuarios=mock(UsuarioRepository.class);
            Usuario gerente=new Usuario();gerente.setRole("GERENTE");
            when(usuarios.findByUsername("gestor")).thenReturn(Optional.of(gerente));
            var target = new PedidoService(pedidos, mock(ClienteRepository.class), mock(ProdutoRepository.class),
                    usuarios, auditoria, mock(PasswordEncoder.class));
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(new MongoTransactionManager(factory), new AnnotationTransactionAttributeSource()));
            PedidoService service = (PedidoService) proxy.getProxy();
            var auth = UsernamePasswordAuthenticationToken.authenticated("gestor", null, List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));

            doThrow(new IllegalStateException("auditoria indisponível")).when(auditoria).registrar(any(), any(), any(), any(), anyMap(), anyMap(), any());
            assertThrows(IllegalStateException.class, () -> service.aplicarDesconto(id, BigDecimal.TEN, auth));
            assertEquals(0, new BigDecimal("100").compareTo(pedidos.findById(id).orElseThrow().getValorTotal()));
            assertEquals(0, registros.count());

            reset(auditoria);
            doThrow(new IllegalStateException("pedido indisponível")).when(pedidos).save(any(Pedido.class));
            assertThrows(IllegalStateException.class, () -> service.aplicarDesconto(id, BigDecimal.TEN, auth));
            assertEquals(0, registros.count());

            // Auditoria precede exclusão; falha na exclusão também reverte o evento.
            doThrow(new IllegalStateException("exclusão indisponível")).when(pedidos).delete(any(Pedido.class));
            assertThrows(IllegalStateException.class, () -> service.excluir(id, auth));
            assertTrue(pedidos.existsById(id)); assertEquals(0, registros.count());
            reset(pedidos);
            service.aplicarDesconto(id, BigDecimal.TEN, auth);
            assertEquals(0, new BigDecimal("90").compareTo(pedidos.findById(id).orElseThrow().getValorTotal()));
            assertEquals(1, registros.count());
            service.aplicarDesconto(id,new BigDecimal("20"),auth);
            assertEquals(0,new BigDecimal("80").compareTo(pedidos.findById(id).orElseThrow().getValorTotal()));
            assertTrue(registros.findAll().stream().anyMatch(e->"DESCONTO_SUBSTITUIDO".equals(e.getOperacao())));
            doThrow(new IllegalStateException("falha ao auditar remoção")).when(auditoria).registrar(any(),eq("DESCONTO_REMOVIDO"),any(),any(),anyMap(),anyMap(),any());
            assertThrows(IllegalStateException.class,()->service.removerDesconto(id,auth));
            assertEquals(0,new BigDecimal("80").compareTo(pedidos.findById(id).orElseThrow().getValorTotal()));
            assertEquals(2,registros.count());
            reset(auditoria);
            service.removerDesconto(id,auth);
            assertEquals(0,new BigDecimal("100").compareTo(pedidos.findById(id).orElseThrow().getValorTotal()));
            var evento=registros.findAll().stream().filter(e->"DESCONTO_REMOVIDO".equals(e.getOperacao())).findFirst().orElseThrow();
            assertEquals("80.00",evento.getValoresAnteriores().get("valorTotal"));
            assertEquals("100.00",evento.getValoresNovos().get("valorTotal"));assertNotNull(evento.getDataHora());
            assertEquals("gestor",evento.getUsuario());assertEquals("GERENTE",evento.getPerfil());assertTrue(evento.getCamposAlterados().contains("valorDesconto"));
            var dashboard=new DashboardService(mongo);String hoje=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
            var indicadores=dashboard.consultar(dashboard.filtro(hoje,hoje,null,null)).indicadores();
            assertEquals(new BigDecimal("100.00"),indicadores.faturamento());assertEquals(new BigDecimal("100.00"),indicadores.ticketMedio());
            assertEquals(new BigDecimal("0.00"),indicadores.descontos());
        }
    }
}
