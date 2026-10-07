package com.umc.pediupatrao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import com.umc.pediupatrao.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, PedidoController.class})
@Import({SecurityConfig.class, PedidoService.class})
class PedidoRF02Test {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PedidoRepository pedidos;
    @MockitoBean ClienteRepository clientes;
    @MockitoBean ProdutoRepository produtos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean ProdutoService produtoService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean UsuarioDetailsService details;

    private void dadosConfiaveis(String perfil) {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente");
        when(clientes.findById("c1")).thenReturn(Optional.of(cliente));
        Usuario usuario = new Usuario();
        usuario.setId("u1");
        usuario.setUsername("operador");
        usuario.setRole(perfil);
        usuario.setPassword(encoder.encode("senha"));
        when(usuarios.findByUsername("operador")).thenReturn(Optional.of(usuario));
        for (Produto produto : List.of(produto("pizza", "Pizza do catálogo", 30.50), produto("bebida", "Bebida do catálogo", 7.25))) {
            when(produtos.findById(produto.getId())).thenReturn(Optional.of(produto));
        }
        when(pedidos.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido pedido = i.getArgument(0);
            pedido.setId("p1");
            return pedido;
        });
    }

    private Produto produto(String id, String nome, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private String corpoForjado(String tipoDesconto, String desconto) {
        return """
            {"clienteId":"c1", "tipoPedido":"RETIRADA", "formaPagamento":"PIX",
             "itens":[{"produtoId":"pizza","nomeProduto":"Nome forjado","quantidade":2,
                       "precoUnitario":0.01,"preco":0.01,"subtotal":0.02},
                      {"produtoId":"bebida","quantidade":3,"precoUnitario":0.01}],
             "subtotalProdutos":0.05,"subtotal":0.05,"valorTotal":0.01,"total":0.01,
             "valorDesconto":99999,"descontoPercentual":0,"tipoDesconto":"%s","descontoInformado":%s,
             "usuarioResponsavelId":"forjado","usuarioResponsavelUsername":"intruso",
             "dataHora":"2030-01-01T00:00:00Z","status":"FINALIZADO"}
            """.formatted(tipoDesconto, desconto);
    }

    private MockHttpServletRequestBuilder criar(String corpo, String perfil) {
        return post("/api/pedidos").with(user("operador").roles(perfil)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)
                .header("X-Gerente-Usuario", "operador").header("X-Gerente-Senha", "senha");
    }

    @Test void apiIgnoraPrecosSubtotalTotalEDescontoEmReaisForjados() throws Exception {
        dadosConfiaveis("ATENDENTE");
        Instant antes = Instant.now();
        String resposta = mvc.perform(criar(corpoForjado("SEM_DESCONTO", "99"), "ATENDENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotalProdutos").value(82.75))
                .andExpect(jsonPath("$.valorTotal").value(82.75)).andExpect(jsonPath("$.valorDesconto").value(0))
                .andExpect(jsonPath("$.descontoInformado").value(0))
                .andExpect(jsonPath("$.itens[0].produtoId").value("pizza"))
                .andExpect(jsonPath("$.itens[0].nomeProduto").value("Pizza do catálogo"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(30.50))
                .andExpect(jsonPath("$.itens[0].subtotal").value(61))
                .andExpect(jsonPath("$.itens[1].precoUnitario").value(7.25))
                .andExpect(jsonPath("$.itens[1].subtotal").value(21.75))
                .andExpect(jsonPath("$.usuarioResponsavelId").value("u1"))
                .andExpect(jsonPath("$.usuarioResponsavelUsername").value("operador"))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andReturn().getResponse().getContentAsString();
        Instant entrada = Instant.parse(mapper.readTree(resposta).get("dataHora").asText());
        assertFalse(entrada.isBefore(antes));
        assertFalse(entrada.isAfter(Instant.now()));
        var captura = org.mockito.ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).save(captura.capture());
        Pedido salvo = captura.getValue();
        assertEquals(new BigDecimal("82.75"), salvo.getSubtotalProdutos());
        assertEquals(new BigDecimal("82.75"), salvo.getValorTotal());
        assertEquals(new BigDecimal("30.50"), salvo.getItens().get(0).getPrecoUnitario());
    }

    @Test void gerenteComDescontoAutorizadoTemTotalRecalculadoMesmoComValoresForjados() throws Exception {
        dadosConfiaveis("GERENTE");
        mvc.perform(criar(corpoForjado("PERCENTUAL", "20"), "GERENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotalProdutos").value(82.75))
                .andExpect(jsonPath("$.descontoPercentual").value(20))
                .andExpect(jsonPath("$.valorDesconto").value(16.55))
                .andExpect(jsonPath("$.valorTotal").value(66.20));
        verify(auditoria).registrar(any(), eq("DESCONTO_APLICADO"), eq("PEDIDO"), eq("p1"), anyMap(),
                argThat(m -> "16.55".equals(m.get("valorDesconto")) && "66.20".equals(m.get("valorTotal"))), isNull());
    }

    @Test void quantidadeZeroNegativaAusenteOuNaoNumericaNaoPersistePedido() throws Exception {
        dadosConfiaveis("ATENDENTE");
        for (String quantidade : List.of("0", "-1", "null", "\"invalida\"", "2147483648")) {
            String corpo = """
                {"clienteId":"c1","tipoPedido":"RETIRADA","formaPagamento":"PIX",
                 "itens":[{"produtoId":"pizza","quantidade":%s}]}
                """.formatted(quantidade);
            mvc.perform(criar(corpo, "ATENDENTE")).andExpect(status().isBadRequest());
        }
        mvc.perform(criar("""
            {"clienteId":"c1","tipoPedido":"RETIRADA","formaPagamento":"PIX","itens":[{"produtoId":"pizza"}]}
            """, "ATENDENTE")).andExpect(status().isBadRequest());
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void quantidadeFracionariaNaoPodeSerTruncadaPelaApi() throws Exception {
        dadosConfiaveis("ATENDENTE");
        mvc.perform(criar("""
            {"clienteId":"c1","tipoPedido":"RETIRADA","formaPagamento":"PIX",
             "itens":[{"produtoId":"pizza","quantidade":1.5}]}
            """, "ATENDENTE")).andExpect(status().isBadRequest());
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void criacaoMantemRestricoesRF04NaApi() throws Exception {
        for (String perfil : List.of("ADMIN", "ATENDENTE")) {
            mvc.perform(criar(corpoForjado("PERCENTUAL", "10"), perfil)).andExpect(status().isForbidden());
        }
        dadosConfiaveis("GERENTE");
        for (String percentual : List.of("-1", "20.01")) {
            mvc.perform(criar(corpoForjado("PERCENTUAL", percentual), "GERENTE")).andExpect(status().isBadRequest());
        }
        mvc.perform(criar(corpoForjado("VALOR", "10"), "GERENTE")).andExpect(status().isBadRequest());
        mvc.perform(criar(corpoForjado("PERCENTUAL", "10"), "GERENTE")
                .header("X-Gerente-Senha", "incorreta")).andExpect(status().isForbidden());
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void leituraPreservaItensHistoricosEPrecoMesmoSeCatalogoMudar() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("antigo");
        Pedido.ItemPedido item = new Pedido.ItemPedido();
        item.setProdutoId("pizza");
        item.setNomeProduto("Nome usado no pedido");
        item.setQuantidade(2);
        item.setPrecoUnitario(new BigDecimal("30.50"));
        pedido.setItens(List.of(item));
        pedido.setValorTotal(new BigDecimal("61.00"));
        when(pedidos.findAll()).thenReturn(List.of(pedido));
        mvc.perform(get("/api/pedidos").with(user("operador").roles("ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].itens[0].nomeProduto").value("Nome usado no pedido"))
                .andExpect(jsonPath("$[0].itens[0].precoUnitario").value(30.5))
                .andExpect(jsonPath("$[0].valorTotal").value(61));
        verifyNoInteractions(produtos);
        verify(pedidos, never()).save(any());
    }

    @Test void pedidoLegadoSemItensContinuaLegivelSemRecalculo() throws Exception {
        Pedido legado = new Pedido();
        legado.setId("legado");
        legado.setTipoPizza("Grande");
        legado.setSabor("Mussarela");
        legado.setQuantidade(2);
        legado.setTipoDesconto(Pedido.TipoDesconto.VALOR);
        legado.setValorTotal(new BigDecimal("50.00"));
        when(pedidos.findAll()).thenReturn(List.of(legado));
        mvc.perform(get("/api/pedidos").with(user("operador").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tipoPizza").value("Grande"))
                .andExpect(jsonPath("$[0].sabor").value("Mussarela"))
                .andExpect(jsonPath("$[0].quantidade").value(2))
                .andExpect(jsonPath("$[0].itens").isEmpty())
                .andExpect(jsonPath("$[0].tipoDesconto").value("VALOR"))
                .andExpect(jsonPath("$[0].valorTotal").value(50));
        verifyNoInteractions(produtos, auditoria);
        verify(pedidos, never()).save(any());
    }
}
