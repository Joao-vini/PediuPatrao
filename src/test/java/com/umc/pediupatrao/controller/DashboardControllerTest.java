package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.*;
import com.umc.pediupatrao.entity.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class})
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MongoTemplate mongo;
    @MockitoBean UsuarioDetailsService usuarios;
    @Test void administradorConsultaTodasAsRotasSemDadosSensiveis() throws Exception {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());
        mvc.perform(get("/api/admin/dashboard").with(user("administrador").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.pedidos").value(0))
                .andExpect(jsonPath("$.clienteNome").doesNotExist());
        for (String rota : List.of("indicadores", "vendas", "status", "produtos")) {
            mvc.perform(get("/api/admin/dashboard/" + rota).with(user("administrador").roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }
    @Test void bloqueiaGerenteAtendenteEAnonimoAntesDaConsulta() throws Exception {
        for (String rota : List.of("/dashboard", "/api/admin/dashboard", "/api/admin/dashboard/indicadores", "/api/admin/dashboard/vendas", "/api/admin/dashboard/status", "/api/admin/dashboard/produtos")) {
            for (String perfil : List.of("GERENTE", "ATENDENTE")) mvc.perform(get(rota).with(user("operador").roles(perfil))).andExpect(status().isForbidden());
            mvc.perform(get(rota)).andExpect(status().is3xxRedirection());
        }
        verifyNoInteractions(mongo);
    }
    @Test void validaFiltrosAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/admin/dashboard").param("inicio", "invalido").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()); verifyNoInteractions(mongo);
    }
    @Test void renderizaPaginaComFiltrosERecursosReais() throws Exception {
        mvc.perform(get("/dashboard").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(view().name("layout-adminlte")).andExpect(content().string(org.hamcrest.Matchers.containsString("dashboard-filtros")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/dashboard.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/dist/css/adminlte.min.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/dist/js/adminlte.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/plugins/chart.js/Chart.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("main-sidebar sidebar-dark-success")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-widget=\"pushmenu\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dash-tipos-grafico")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("bootstrap@5"))));
        verifyNoInteractions(mongo);
    }
    @Test void falhaDoBancoRetorna503SemDetalhesInternos() throws Exception {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("detalhe-interno"));
        mvc.perform(get("/api/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalhe-interno"))));
    }
    @Test void filtrosAplicamSeAosCincoEndpointsEErrosTemJson() throws Exception {
        for (String sufixo : List.of("", "/indicadores", "/vendas", "/status", "/produtos")) {
            mvc.perform(get("/api/admin/dashboard" + sufixo).param("tipo", "TIPO_INVALIDO").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isString())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(mongo);
    }
    @Test void identificaAdministradorComEscapeDeHtml() throws Exception {
        mvc.perform(get("/dashboard").with(user("<admin>").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;admin&gt;")));
    }
}
