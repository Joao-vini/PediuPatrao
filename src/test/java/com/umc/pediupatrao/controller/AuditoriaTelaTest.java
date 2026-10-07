package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class AuditoriaTelaTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean ProdutoService produtos;
    @MockitoBean PedidoService pedidos;
    @MockitoBean ClienteService clientes;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean UsuarioDetailsService details;

    @Test void adminEGerenteConsultamFiltrosEDetalhesComPaginacao() throws Exception {
        Auditoria evento = new Auditoria();
        evento.setId("evento-1");
        evento.setEntidadeId("pedido-1");
        evento.setUsuario("ana");
        evento.setPerfil("ATENDENTE");
        evento.setOperacao("PEDIDO_CRIADO");
        evento.setEntidade("PEDIDO");
        evento.setDataHora(Instant.parse("2026-10-06T17:16:32Z"));
        evento.setValoresNovos(Map.of("operador", "ana", "gerenteAutorizador", "gerente",
                "senhaGerente", "nunca-exibir", "observacao", "<script>alert(1)</script>"));
        var seguro = new AuditoriaService(null, null).paraConsulta(evento);
        when(auditoria.operacoesDisponiveis()).thenReturn(Map.of("PEDIDO_CRIADO", "Pedido criado"));
        when(auditoria.consultar("ana", "PEDIDO_CRIADO", "PEDIDO", "2026-10-06", "2026-10-07", 1))
                .thenReturn(new PageImpl<>(List.of(seguro), PageRequest.of(1, 20), 45));
        for (String perfil : List.of("ADMIN", "GERENTE")) {
            mvc.perform(get("/auditoria").with(user("gestor").roles(perfil))
                    .param("usuario", "ana").param("operacao", "PEDIDO_CRIADO").param("entidade", "PEDIDO")
                    .param("dataInicial", "2026-10-06").param("dataFinal", "2026-10-07").param("pagina", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("06/10/2026 14:16:32")))
                    .andExpect(content().string(containsString("Pedido criado")))
                    .andExpect(content().string(containsString("Ver detalhes")))
                    .andExpect(content().string(containsString("pedido-1")))
                    .andExpect(content().string(containsString("Gerente autorizador")))
                    .andExpect(content().string(containsString("pagina=2&amp;usuario=ana&amp;operacao=PEDIDO_CRIADO&amp;entidade=PEDIDO&amp;dataInicial=2026-10-06&amp;dataFinal=2026-10-07")))
                    .andExpect(content().string(not(containsString("nunca-exibir"))))
                    .andExpect(content().string(not(containsString("senhaGerente"))))
                    .andExpect(content().string(containsString("&lt;script&gt;")));
        }
        verify(auditoria, times(2)).consultar("ana", "PEDIDO_CRIADO", "PEDIDO", "2026-10-06", "2026-10-07", 1);
        verify(auditoria, never()).listar();
    }

    @Test void atendenteEAnonimoNaoAcessamAuditoria() throws Exception {
        mvc.perform(get("/auditoria").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/auditoria")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(auditoria);
    }

    @Test void nenhumPerfilPodeCriarEditarSubstituirOuExcluirAuditoriaPorHttp() throws Exception {
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            for (String rota : List.of("/auditoria", "/auditoria/evento-1", "/api/auditoria", "/api/auditoria/evento-1")) {
                for (var requisicao : List.of(post(rota), put(rota), patch(rota), delete(rota))) {
                    mvc.perform(requisicao.with(user("operador").roles(perfil)).with(csrf())
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"usuario\":\"forjado\"}")).andExpect(status().isForbidden());
                }
            }
        }
        verifyNoInteractions(auditoria);
    }

    @Test void periodoInvalidoExibeErroEPreservaFiltros() throws Exception {
        when(auditoria.consultar("ana", "", "", "2026-10-07", "2026-10-06", 0))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial não pode ser posterior à data final."));
        mvc.perform(get("/auditoria").with(user("admin").roles("ADMIN"))
                .param("usuario", "ana").param("dataInicial", "2026-10-07").param("dataFinal", "2026-10-06"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("erro"))
                .andExpect(content().string(containsString("value=\"ana\"")))
                .andExpect(content().string(containsString("value=\"2026-10-07\"")));
    }
}
