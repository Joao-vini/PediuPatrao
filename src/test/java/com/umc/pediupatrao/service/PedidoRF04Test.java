package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoRF04Test {
    @Mock PedidoRepository pedidos;
    @Mock ClienteRepository clientes;
    @Mock ProdutoRepository produtos;
    @Mock UsuarioRepository usuarios;
    @Mock AuditoriaService auditoria;
    @Mock PasswordEncoder encoder;
    @InjectMocks PedidoService service;

    private Authentication auth(String perfil) {
        return new UsernamePasswordAuthenticationToken("gestor", null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
    }

    private Pedido pedido(String status) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setStatus(status);
        p.setSubtotalProdutos(new BigDecimal("100.00"));
        p.setValorTotal(new BigDecimal("100.00"));
        p.setTaxaEntrega(BigDecimal.ZERO);
        p.setFormaPagamento(Pedido.FormaPagamento.PIX);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    private Usuario gerente() {
        Usuario gerente = new Usuario();
        gerente.setId("g1");
        gerente.setUsername("gestor");
        gerente.setRole("GERENTE");
        gerente.setPassword("hash");
        when(usuarios.findByUsername("gestor")).thenReturn(Optional.of(gerente));
        when(encoder.matches("senha", "hash")).thenReturn(true);
        return gerente;
    }

    @Test void gerenteAplicaVintePorCentoAntesDaSaidaComAuditoria() {
        Pedido p = pedido("PRONTO_PARA_RETIRADA");
        when(pedidos.save(p)).thenReturn(p);
        Authentication gerente = auth("GERENTE");
        assertSame(p, service.aplicarDesconto("p1", new BigDecimal("20"), gerente));
        assertEquals(new BigDecimal("20.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("80.00"), p.getValorTotal());
        verify(auditoria).registrar(eq(gerente), eq("DESCONTO_APLICADO"), eq("PEDIDO"), eq("p1"), anyMap(), anyMap(), isNull());
    }

    @Test void gerenteCancelaComJustificativaEAuditoria() {
        Pedido p = pedido("EM_PREPARO");
        gerente();
        when(pedidos.save(p)).thenReturn(p);
        Authentication gerente = auth("GERENTE");
        service.cancelar("p1", " Cliente desistiu ", "gestor", "senha", gerente);
        assertEquals("CANCELADO", p.getStatus());
        assertEquals("Cliente desistiu", p.getMotivoCancelamento());
        assertEquals("gestor", p.getCanceladoPorUsername());
        assertNotNull(p.getCanceladoEm());
        verify(auditoria).registrar(eq(gerente), eq("PEDIDO_CANCELADO"), eq("PEDIDO"), eq("p1"), anyMap(),
                argThat(m -> "gestor".equals(m.get("gerenteAutorizador")) && !m.containsKey("senha")), eq("Cliente desistiu"));
    }

    @Test void adminEAtendenteSaoBloqueadosMesmoComCredenciaisDeGerente() {
        for (String perfil : List.of("ADMIN", "ATENDENTE")) {
            Authentication usuario = auth(perfil);
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.aplicarDesconto("p1", BigDecimal.TEN, usuario)).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.cancelar("p1", "Motivo", "gestor", "senha", usuario)).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.autorizarDescontoNovoPedido(List.of("prod1"), List.of(1), Pedido.TipoPedido.RETIRADA,
                            BigDecimal.ZERO, Pedido.TipoDesconto.PERCENTUAL, BigDecimal.TEN, "gestor", "senha", usuario)).getStatusCode());
            for (boolean legado : List.of(false, true)) {
                Pedido p = new Pedido();
                if (legado) p.setDescontoPercentual(BigDecimal.TEN);
                else {
                    p.setTipoDesconto(Pedido.TipoDesconto.PERCENTUAL);
                    p.setDescontoInformado(BigDecimal.TEN);
                }
                assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                        () -> service.criarPedido(p, "gestor", "senha", usuario)).getStatusCode());
            }
        }
        verifyNoInteractions(pedidos, clientes, produtos, usuarios, auditoria, encoder);
    }

    @Test void rejeitaPercentualNegativoAcimaDeVinteOuAusente() {
        pedido("EM_PREPARO");
        for (BigDecimal percentual : new BigDecimal[]{new BigDecimal("-0.01"), new BigDecimal("20.01"), null}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.aplicarDesconto("p1", percentual, auth("GERENTE"))).getStatusCode());
        }
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void rejeitaDescontoEmValorSemImpedirLeituraDoTipoLegado() {
        Pedido p = pedido("EM_PREPARO");
        p.setTipoDesconto(Pedido.TipoDesconto.VALOR);
        assertSame(p, service.buscarPorId("p1"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.aplicarDesconto("p1", Pedido.TipoDesconto.VALOR, BigDecimal.TEN, auth("GERENTE"))).getStatusCode());
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void descontoRecusadoDepoisDaSaidaRetiradaOuCancelamento() {
        for (String status : new String[]{"SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO", null}) {
            pedido(status);
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.aplicarDesconto("p1", BigDecimal.TEN, auth("GERENTE"))).getStatusCode());
        }
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void justificativaObrigatoriaValidadaNoBackend() {
        pedido("EM_PREPARO");
        gerente();
        for (String motivo : new String[]{null, "", "  ", "a".repeat(501)}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.cancelar("p1", motivo, "gestor", "senha", auth("GERENTE"))).getStatusCode());
        }
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test void gerenteCriaPedidoComDescontoPercentualAuditado() {
        when(clientes.findById("c1")).thenReturn(Optional.of(new Cliente()));
        gerente();
        Produto produto = new Produto();
        produto.setId("prod1");
        produto.setNome("Pizza");
        produto.setPreco(100.0);
        produto.setAtivo(true);
        when(produtos.findById("prod1")).thenReturn(Optional.of(produto));
        when(pedidos.save(any())).thenAnswer(i -> i.getArgument(0));
        Pedido p = new Pedido();
        p.setId("p1");
        p.setClienteId("c1");
        p.setTipoPedido(Pedido.TipoPedido.RETIRADA);
        p.setFormaPagamento(Pedido.FormaPagamento.PIX);
        p.setTipoDesconto(Pedido.TipoDesconto.PERCENTUAL);
        p.setDescontoInformado(new BigDecimal("20"));
        Pedido.ItemPedido item = new Pedido.ItemPedido();
        item.setProdutoId("prod1");
        item.setQuantidade(1);
        p.setItens(List.of(item));
        Authentication gerente = auth("GERENTE");
        assertEquals(new BigDecimal("80.00"), service.criarPedido(p, "gestor", "senha", gerente).getValorTotal());
        verify(auditoria).registrar(eq(gerente), eq("DESCONTO_APLICADO"), eq("PEDIDO"), any(), anyMap(),
                argThat(m -> "PERCENTUAL".equals(m.get("tipoDesconto")) && "20".equals(m.get("valorInformado"))), isNull());
    }
}
