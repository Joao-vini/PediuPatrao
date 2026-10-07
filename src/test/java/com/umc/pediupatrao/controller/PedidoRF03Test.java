package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import com.umc.pediupatrao.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Usa o PedidoService real nas requisições HTTP; somente os repositórios são simulados.
@WebMvcTest({HomeController.class, PedidoController.class})
@Import({SecurityConfig.class, PedidoService.class})
class PedidoRF03Test {
    @Autowired MockMvc mvc;
    @Autowired PedidoService service;
    @MockitoBean PedidoRepository pedidos;
    @MockitoBean ClienteRepository clientes;
    @MockitoBean ProdutoRepository produtos;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean ProdutoService produtoService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean UsuarioDetailsService details;

    private Authentication auth(String nome) {
        return new UsernamePasswordAuthenticationToken(nome, null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
    }

    private void usuario(String nome, String id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(nome);
        usuario.setRole("ATENDENTE");
        when(usuarios.findByUsername(nome)).thenReturn(Optional.of(usuario));
    }

    private Pedido existente(String status, Pedido.TipoPedido tipo) {
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setStatus(status);
        pedido.setTipoPedido(tipo);
        pedido.setDataHora(Instant.parse("2026-10-01T12:00:00Z"));
        pedido.setUsuarioResponsavelId("entrada-id");
        pedido.setUsuarioResponsavelUsername("entrada");
        when(pedidos.findById("p1")).thenReturn(Optional.of(pedido));
        when(pedidos.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        return pedido;
    }

    @Test void criacaoHttpForcaRecebidoEEntradaDoServidorIgnorandoMetadadosForjados() throws Exception {
        when(clientes.findById("c1")).thenReturn(Optional.of(new Cliente()));
        usuario("entrada", "entrada-id");
        Produto produto = new Produto();
        produto.setId("prod1");
        produto.setNome("Pizza");
        produto.setPreco(30.0);
        produto.setAtivo(true);
        when(produtos.findById("prod1")).thenReturn(Optional.of(produto));
        when(pedidos.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido pedido = i.getArgument(0);
            pedido.setId("p1");
            return pedido;
        });
        Instant antes = Instant.now();
        String resposta = mvc.perform(post("/api/pedidos").with(user("entrada").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"clienteId":"c1", "tipoPedido":"RETIRADA", "formaPagamento":"PIX",
                     "itens":[{"produtoId":"prod1","quantidade":1}], "status":"FINALIZADO",
                     "dataHora":"2030-01-01T00:00:00Z", "usuarioResponsavelId":"forjado",
                     "usuarioResponsavelUsername":"intruso", "dataHoraSaida":"2030-01-01T00:00:00Z",
                     "usuarioSaidaId":"forjado", "usuarioSaidaUsername":"intruso"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.usuarioResponsavelId").value("entrada-id"))
                .andExpect(jsonPath("$.usuarioResponsavelUsername").value("entrada"))
                .andExpect(jsonPath("$.dataHoraSaida").isEmpty())
                .andExpect(jsonPath("$.usuarioSaidaId").isEmpty())
                .andExpect(jsonPath("$.usuarioSaidaUsername").isEmpty())
                .andExpect(content().string(not(containsString("intruso"))))
                .andReturn().getResponse().getContentAsString();
        Instant entrada = Instant.parse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(resposta).get("dataHora").asText());
        assertFalse(entrada.isBefore(antes));
        assertFalse(entrada.isAfter(Instant.now()));
        verify(auditoria).registrar(any(), eq("PEDIDO_CRIADO"), eq("PEDIDO"), eq("p1"), anyMap(),
                argThat(m -> "RECEBIDO".equals(m.get("status")) && "entrada".equals(m.get("operador"))), isNull());
    }

    @Test void fluxoCompletoEntregaRegistraSaidaEAuditaTodasEtapas() throws Exception {
        fluxo(Pedido.TipoPedido.ENTREGA, "SAIU_PARA_ENTREGA");
    }

    @Test void fluxoCompletoRetiradaRegistraSaidaEAuditaTodasEtapas() throws Exception {
        fluxo(Pedido.TipoPedido.RETIRADA, "RETIRADO");
    }

    private void fluxo(Pedido.TipoPedido tipo, String saida) throws Exception {
        Pedido pedido = existente("RECEBIDO", tipo);
        usuario("saida", "saida-id");
        Instant entrada = pedido.getDataHora();
        Instant horarioSaida = null;
        String anterior = "RECEBIDO";
        for (String proximo : List.of("EM_PREPARO", "PRONTO", saida, "FINALIZADO")) {
            Instant antes = Instant.now();
            mvc.perform(put("/api/pedidos/p1/status").param("status", proximo)
                    .param("dataHoraSaida", "2030-01-01T00:00:00Z").param("usuarioSaidaId", "forjado")
                    .param("usuarioSaidaUsername", "intruso").with(user("saida").roles("ATENDENTE")).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(proximo));
            verify(auditoria).registrar(argThat(a -> "saida".equals(a.getName())
                    && a.getAuthorities().stream().anyMatch(r -> "ROLE_ATENDENTE".equals(r.getAuthority()))),
                    eq("STATUS_PEDIDO_ALTERADO"), eq("PEDIDO"), eq("p1"),
                    eq(Map.of("status", anterior)), eq(Map.of("status", proximo)), isNull());
            assertEquals(entrada, pedido.getDataHora());
            assertEquals("entrada-id", pedido.getUsuarioResponsavelId());
            assertEquals("entrada", pedido.getUsuarioResponsavelUsername());
            if (proximo.equals(saida)) {
                horarioSaida = pedido.getDataHoraSaida();
                assertNotNull(horarioSaida);
                assertFalse(horarioSaida.isBefore(antes));
                assertFalse(horarioSaida.isAfter(Instant.now()));
                assertEquals("saida-id", pedido.getUsuarioSaidaId());
                assertEquals("saida", pedido.getUsuarioSaidaUsername());
            } else if ("FINALIZADO".equals(proximo)) {
                assertEquals(horarioSaida, pedido.getDataHoraSaida());
                assertEquals("saida", pedido.getUsuarioSaidaUsername());
            } else {
                assertNull(pedido.getDataHoraSaida());
                assertNull(pedido.getUsuarioSaidaUsername());
            }
            anterior = proximo;
        }
        verify(pedidos, times(4)).save(pedido);
        verify(auditoria, times(4)).registrar(any(), eq("STATUS_PEDIDO_ALTERADO"), eq("PEDIDO"), eq("p1"), anyMap(), anyMap(), isNull());
    }

    @Test void serviceRejeitaSaltosRetrocessosEstadosRepetidosECancelados() {
        List<String> estados = List.of("RECEBIDO", "EM_PREPARO", "PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");
        for (Pedido.TipoPedido tipo : List.of(Pedido.TipoPedido.ENTREGA, Pedido.TipoPedido.RETIRADA)) {
            for (String atual : estados) {
                Pedido pedido = existente(atual, tipo);
                String permitido = service.proximoStatus(pedido);
                for (String destino : estados) {
                    if (destino.equals(permitido)) continue;
                    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                            () -> service.atualizarStatus("p1", destino, auth("saida"))).getStatusCode());
                    assertEquals(atual, pedido.getStatus());
                    assertNull(pedido.getDataHoraSaida());
                }
            }
        }
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria, usuarios);
    }

    @Test void httpInvalidoERejeitadoPeloServiceRealSemSalvar() throws Exception {
        for (String atual : List.of("RECEBIDO", "EM_PREPARO", "PRONTO", "CANCELADO")) {
            Pedido pedido = existente(atual, Pedido.TipoPedido.ENTREGA);
            mvc.perform(put("/api/pedidos/p1/status").param("status", "FINALIZADO")
                    .with(user("saida").roles("ATENDENTE")).with(csrf())).andExpect(status().isBadRequest());
            assertEquals(atual, pedido.getStatus());
        }
        existente("RECEBIDO", Pedido.TipoPedido.RETIRADA);
        mvc.perform(post("/pedidos/p1/status").param("status", "PRONTO")
                .with(user("saida").roles("ATENDENTE")).with(csrf()))
                .andExpect(redirectedUrl("/pedidos")).andExpect(flash().attributeExists("erro"));
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void saidaSemUsuarioAutenticadoEncontradoNaoMudaPedido() {
        Pedido pedido = existente("PRONTO", Pedido.TipoPedido.RETIRADA);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("p1", "RETIRADO", auth("inexistente"))).getStatusCode());
        assertEquals("PRONTO", pedido.getStatus());
        assertNull(pedido.getDataHoraSaida());
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void estadoLegadoProntoParaRetiradaExigeRetiradoAntesDeFinalizar() {
        Pedido pedido = existente("PRONTO_PARA_RETIRADA", null);
        usuario("saida", "saida-id");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.atualizarStatus("p1", "FINALIZADO", auth("saida"))).getStatusCode());
        service.atualizarStatus("p1", "RETIRADO", auth("saida"));
        assertNotNull(pedido.getDataHoraSaida());
        service.atualizarStatus("p1", "FINALIZADO", auth("saida"));
        assertEquals("FINALIZADO", pedido.getStatus());
        assertNull(pedido.getTipoPedido());
    }

    @Test void leituraLegadaNaoReescreveDadosNemInventaSaida() {
        Pedido pedido = existente("EM_PREPARACAO", null);
        assertSame(pedido, service.buscarPorId("p1"));
        assertEquals("PRONTO", service.proximoStatus(pedido));
        assertEquals("EM_PREPARACAO", pedido.getStatus());
        pedido.setStatus("PRONTO");
        assertNull(service.proximoStatus(pedido)); // Sem tipo, não há como escolher entrega ou retirada.
        pedido.setStatus("SAIU_PARA_ENTREGA");
        assertEquals("FINALIZADO", service.proximoStatus(pedido));
        pedido.setStatus("RETIRADO");
        assertEquals("FINALIZADO", service.proximoStatus(pedido));
        pedido.setStatus("ENTREGUE");
        assertNull(service.proximoStatus(pedido));
        assertNull(pedido.getDataHoraSaida());
        assertNull(pedido.getUsuarioSaidaUsername());
        verify(pedidos, never()).save(any());
    }

    @Test void balcaoExistenteSegueEtapaDeRetiradaSemFinalizacaoDireta() {
        Pedido pedido = existente("PRONTO", Pedido.TipoPedido.BALCAO);
        assertEquals("RETIRADO", service.proximoStatus(pedido));
        assertThrows(ResponseStatusException.class, () -> service.atualizarStatus("p1", "FINALIZADO", auth("saida")));
        verify(pedidos, never()).save(any());
    }
}
