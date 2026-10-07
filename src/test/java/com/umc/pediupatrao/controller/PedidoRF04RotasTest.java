package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, PedidoController.class})
@Import(SecurityConfig.class)
class PedidoRF04RotasTest {
    @Autowired MockMvc mvc;
    @MockitoBean PedidoService pedidos;
    @MockitoBean ProdutoService produtos;
    @MockitoBean ClienteService clientes;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean UsuarioDetailsService details;

    private MockHttpServletRequestBuilder requisicao(String rota, String perfil) {
        return post(rota).with(user("gestor").roles(perfil)).with(csrf())
                .param("percentual", "20").param("tipoDesconto", "PERCENTUAL").param("descontoInformado", "20")
                .param("justificativa", "Cliente desistiu").param("usuarioGerente", "gestor").param("senhaGerente", "senha")
                .param("usuarioGerenteDesconto", "gestor").param("senhaGerenteDesconto", "senha")
                .param("produtoIds", "prod1").param("quantidades", "1").param("tipoPedido", "RETIRADA");
    }

    @Test void adminEAtendenteRecebem403EmTodosEndpointsRF04() throws Exception {
        for (String perfil : List.of("ADMIN", "ATENDENTE")) {
            for (String rota : List.of("/api/pedidos/p1/desconto", "/api/pedidos/p1/cancelamento",
                    "/pedidos/p1/desconto", "/pedidos/p1/cancelamento", "/pedidos/autorizar-desconto", "/api/pedidos/p1/cancelar")) {
                mvc.perform(requisicao(rota, perfil)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(pedidos);
    }

    @Test void gerenteAcessaOperacoesPelaApiEInterface() throws Exception {
        Pedido p = new Pedido();
        p.setId("p1");
        when(pedidos.aplicarDesconto(eq("p1"), any(BigDecimal.class), any())).thenReturn(p);
        when(pedidos.cancelar(eq("p1"), anyString(), anyString(), anyString(), any())).thenReturn(p);
        when(pedidos.autorizarDescontoNovoPedido(anyList(), anyList(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(Map.of("valorDesconto", new BigDecimal("20.00")));
        mvc.perform(requisicao("/api/pedidos/p1/desconto", "GERENTE")).andExpect(status().isOk());
        mvc.perform(requisicao("/api/pedidos/p1/cancelamento", "GERENTE")).andExpect(status().isOk());
        mvc.perform(requisicao("/pedidos/p1/desconto", "GERENTE")).andExpect(redirectedUrl("/pedidos/p1"));
        mvc.perform(requisicao("/pedidos/p1/cancelamento", "GERENTE")).andExpect(redirectedUrl("/pedidos"));
        mvc.perform(requisicao("/pedidos/autorizar-desconto", "GERENTE")).andExpect(status().isOk());
        verify(pedidos, times(2)).cancelar(eq("p1"), eq("Cliente desistiu"), eq("gestor"), eq("senha"), any());
    }
}
