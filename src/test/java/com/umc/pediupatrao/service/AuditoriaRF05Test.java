package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaRF05Test {
    @Mock AuditoriaRepository registros;
    @Mock ClienteRepository clientes;
    @Mock PedidoRepository pedidos;
    @Mock ProdutoRepository produtos;
    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder encoder;
    AuditoriaService auditoria;
    ClienteService clienteService;
    PedidoService pedidoService;

    @BeforeEach void preparar() {
        auditoria = new AuditoriaService(registros, null);
        clienteService = new ClienteService();
        ReflectionTestUtils.setField(clienteService, "clienteRepository", clientes);
        ReflectionTestUtils.setField(clienteService, "auditoriaService", auditoria);
        pedidoService = new PedidoService(pedidos, clientes, produtos, usuarios, auditoria, encoder);
    }

    private Authentication auth(String nome, String perfil) {
        return UsernamePasswordAuthenticationToken.authenticated(nome, "credencial-nao-auditar",
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
    }

    private Auditoria evento() {
        var captura = ArgumentCaptor.forClass(Auditoria.class);
        verify(registros).save(captura.capture());
        return captura.getValue();
    }

    private Cliente cliente() {
        Cliente c = new Cliente();
        c.setId("c1"); c.setNome("Cliente"); c.setTelefone("11999990000");
        c.setCep("01001000"); c.setLogradouro("Rua antiga"); c.setNumero("1");
        c.setComplemento("Casa"); c.setBairro("Centro"); c.setCidade("São Paulo");
        c.setEstado("SP"); c.setEndereco("Endereço legado");
        return c;
    }

    private void atualizarCliente(Cliente anterior, Cliente novo) {
        when(clientes.existsById("c1")).thenReturn(true);
        when(clientes.findById("c1")).thenReturn(Optional.of(anterior));
        when(clientes.save(novo)).thenReturn(novo);
        clienteService.salvar(novo, auth("atendente-autenticado", "ATENDENTE"));
    }

    @Test void telefoneRegistraSomenteCampoAlteradoComAntesDepoisEIdentidadeAutenticada() {
        Cliente anterior = cliente(), novo = cliente();
        novo.setTelefone("11888880000");
        Instant inicio = Instant.now();
        atualizarCliente(anterior, novo);
        Auditoria evento = evento();
        assertEquals("ALTERACAO", evento.getOperacao());
        assertEquals("CLIENTE", evento.getEntidade()); assertEquals("c1", evento.getEntidadeId());
        assertEquals(Map.of("telefone", "11999990000"), evento.getValoresAnteriores());
        assertEquals(Map.of("telefone", "11888880000"), evento.getValoresNovos());
        assertEquals(List.of("telefone"), evento.getCamposAlterados());
        assertEquals("atendente-autenticado", evento.getUsuario()); assertEquals("ATENDENTE", evento.getPerfil());
        assertFalse(evento.getDataHora().isBefore(inicio)); assertFalse(evento.getDataHora().isAfter(Instant.now()));
    }

    @Test void enderecoEComponentesRegistramValoresReaisMesmoSeSaveModificarObjetoAnterior() {
        Cliente anterior = cliente(), novo = cliente();
        novo.setCep("02002000"); novo.setLogradouro("Rua nova"); novo.setNumero("2");
        novo.setComplemento("Apartamento"); novo.setBairro("Bairro novo"); novo.setCidade("Santos");
        novo.setEstado("RJ"); novo.setEndereco("Endereço novo");
        when(clientes.existsById("c1")).thenReturn(true);
        when(clientes.findById("c1")).thenReturn(Optional.of(anterior));
        // Exercita um save que reutiliza/muda a instância carregada: o antes precisa estar preservado.
        when(clientes.save(novo)).thenAnswer(i -> {
            anterior.setLogradouro("Rua nova");
            return novo;
        });
        clienteService.salvar(novo, auth("atendente", "ATENDENTE"));
        Auditoria evento = evento();
        assertEquals(Map.of("cep", "01001000", "logradouro", "Rua antiga", "numero", "1",
                "complemento", "Casa", "bairro", "Centro", "cidade", "São Paulo", "estado", "SP",
                "endereco", "Endereço legado"), evento.getValoresAnteriores());
        assertEquals(Map.of("cep", "02002000", "logradouro", "Rua nova", "numero", "2",
                "complemento", "Apartamento", "bairro", "Bairro novo", "cidade", "Santos", "estado", "RJ",
                "endereco", "Endereço novo"), evento.getValoresNovos());
        assertEquals(evento.getValoresNovos().keySet(), java.util.Set.copyOf(evento.getCamposAlterados()));
    }

    @Test void clienteSemMudancaNaoGeraEventoDeAlteracao() {
        atualizarCliente(cliente(), cliente());
        verifyNoInteractions(registros);
    }

    private Pedido pedidoNovo() {
        when(clientes.findById("c1")).thenReturn(Optional.of(cliente()));
        Produto produto = new Produto(); produto.setId("prod1"); produto.setNome("Pizza");
        produto.setPreco(100.0); produto.setAtivo(true);
        when(produtos.findById("prod1")).thenReturn(Optional.of(produto));
        Usuario operador = new Usuario(); operador.setId("g1"); operador.setUsername("gerente-real");
        operador.setRole("GERENTE"); operador.setPassword("hash-nao-auditar");
        when(usuarios.findByUsername("gerente-real")).thenReturn(Optional.of(operador));
        when(pedidos.save(any())).thenAnswer(i -> {
            Pedido p = i.getArgument(0); p.setId("p1"); return p;
        });
        Pedido p = new Pedido(); p.setClienteId("c1"); p.setTipoPedido(Pedido.TipoPedido.RETIRADA);
        p.setFormaPagamento(Pedido.FormaPagamento.PIX);
        p.setUsuarioResponsavelUsername("autor-forjado");
        Pedido.ItemPedido item = new Pedido.ItemPedido(); item.setProdutoId("prod1"); item.setQuantidade(1);
        p.setItens(List.of(item));
        return p;
    }

    @Test void criacaoStatusDescontoECancelamentoPersistemEventosReaisSemCredenciais() {
        Pedido p = pedidoService.criarPedido(pedidoNovo(), auth("gerente-real", "GERENTE"));
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        pedidoService.atualizarStatus("p1", "EM_PREPARO", auth("gerente-real", "GERENTE"));
        pedidoService.aplicarDesconto("p1", new BigDecimal("20"), auth("gerente-real", "GERENTE"));
        when(encoder.matches("senha-nao-auditar", "hash-nao-auditar")).thenReturn(true);
        pedidoService.cancelar("p1", " Cliente desistiu ", "gerente-real", "senha-nao-auditar",
                auth("gerente-real", "GERENTE"));
        var captura = ArgumentCaptor.forClass(Auditoria.class);
        verify(registros, times(4)).save(captura.capture());
        List<Auditoria> eventos = captura.getAllValues();
        assertEquals(List.of("PEDIDO_CRIADO", "STATUS_PEDIDO_ALTERADO", "DESCONTO_APLICADO", "PEDIDO_CANCELADO"),
                eventos.stream().map(Auditoria::getOperacao).toList());
        assertEquals("RECEBIDO", eventos.get(0).getValoresNovos().get("status"));
        assertEquals("100.00", eventos.get(0).getValoresNovos().get("valorTotal"));
        assertEquals(Map.of("status", "RECEBIDO"), eventos.get(1).getValoresAnteriores());
        assertEquals(Map.of("status", "EM_PREPARO"), eventos.get(1).getValoresNovos());
        Auditoria desconto = eventos.get(2);
        assertEquals("100.00", desconto.getValoresAnteriores().get("valorTotal"));
        assertEquals("0.00", desconto.getValoresAnteriores().get("valorDesconto"));
        assertEquals("SEM_DESCONTO", desconto.getValoresAnteriores().get("tipoDesconto"));
        assertEquals("80.00", desconto.getValoresNovos().get("valorTotal"));
        assertEquals("20.00", desconto.getValoresNovos().get("valorDesconto"));
        assertEquals("20.00", desconto.getValoresNovos().get("valorInformado"));
        assertEquals("PERCENTUAL", desconto.getValoresNovos().get("tipoDesconto"));
        assertEquals("EM_PREPARO", eventos.get(3).getValoresAnteriores().get("status"));
        assertEquals("CANCELADO", eventos.get(3).getValoresNovos().get("status"));
        assertEquals("Cliente desistiu", eventos.get(3).getJustificativa());
        assertEquals("gerente-real", eventos.get(3).getValoresNovos().get("operador"));
        assertEquals("gerente-real", eventos.get(3).getValoresNovos().get("gerenteAutorizador"));
        for (Auditoria evento : eventos) {
            assertEquals("gerente-real", evento.getUsuario()); assertEquals("GERENTE", evento.getPerfil());
            assertEquals("PEDIDO", evento.getEntidade()); assertEquals("p1", evento.getEntidadeId());
            assertNotNull(evento.getDataHora());
            String valores = evento.getValoresAnteriores().toString() + evento.getValoresNovos();
            assertFalse(valores.contains("nao-auditar")); assertFalse(valores.contains("autor-forjado"));
        }
        assertEquals("auditorias", Auditoria.class.getAnnotation(Document.class).collection());
        assertTrue(org.springframework.data.mongodb.repository.MongoRepository.class.isAssignableFrom(AuditoriaRepository.class));
    }

    @Test void descontoNaCriacaoRegistraTotalAnteriorSemDesconto() {
        Pedido p = pedidoNovo(); p.setTipoDesconto(Pedido.TipoDesconto.PERCENTUAL);
        p.setDescontoInformado(new BigDecimal("10"));
        when(encoder.matches("senha-nao-auditar", "hash-nao-auditar")).thenReturn(true);
        pedidoService.criarPedido(p, "gerente-real", "senha-nao-auditar", auth("gerente-real", "GERENTE"));
        var captura = ArgumentCaptor.forClass(Auditoria.class);
        verify(registros, times(2)).save(captura.capture());
        Auditoria desconto = captura.getAllValues().get(1);
        assertEquals("DESCONTO_APLICADO", desconto.getOperacao());
        assertEquals("100.00", desconto.getValoresAnteriores().get("valorTotal"));
        assertEquals("90.00", desconto.getValoresNovos().get("valorTotal"));
        assertEquals("0.00", desconto.getValoresAnteriores().get("valorDesconto"));
        assertEquals("10.00", desconto.getValoresNovos().get("valorDesconto"));
    }

    @Test void dadosSensiveisSaoFiltradosAntesDeSalvarEValoresIguaisNaoSaoCamposAlterados() {
        Map<String, String> antes = Map.of("status", "PRONTO", "senhaGerente", "senha-secreta",
                "Password_Hash", "$2a$10$" + "a".repeat(53));
        Map<String, String> depois = Map.of("status", "PRONTO", "token", "token-secreto",
                "credenciais", "credencial-secreta", "observacao", "Bearer token-secreto", "telefone", "11999990000");
        auditoria.registrar(auth("operador", "GERENTE"), "ALTERACAO", "CLIENTE", "c1", antes, depois,
                "password=senha-secreta");
        Auditoria salvo = evento();
        assertEquals(Map.of("status", "PRONTO"), salvo.getValoresAnteriores());
        assertEquals(Map.of("status", "PRONTO", "observacao", "[Dado sensível ocultado]", "telefone", "11999990000"),
                salvo.getValoresNovos());
        assertEquals("[Dado sensível ocultado]", salvo.getJustificativa());
        assertFalse(salvo.getCamposAlterados().contains("status"));
        assertFalse(salvo.getCamposAlterados().contains("senhaGerente"));
        assertEquals("senha-secreta", antes.get("senhaGerente"));
        assertEquals("operador", salvo.getUsuario()); assertEquals("GERENTE", salvo.getPerfil());
        assertFalse(auditoria.paraConsulta(salvo).valoresNovos().toString().contains("token-secreto"));
    }
}
