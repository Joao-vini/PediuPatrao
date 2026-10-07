package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, ProdutoController.class})
@Import(SecurityConfig.class)
class ProdutoGestaoTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProdutoService produtos;
    @MockitoBean PedidoService pedidos;
    @MockitoBean ClienteService clientes;
    @MockitoBean UsuarioService usuarios;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean UsuarioDetailsService details;

    @Test void atendenteNaoAcessaGestaoNemApiDiretamente() throws Exception {
        for (String rota : List.of("/produtos", "/produtos/editar/p1", "/api/produtos")) {
            mvc.perform(get(rota).with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
        }
        for (String rota : List.of("/produtos", "/produtos/editar/p1", "/produtos/p1/status", "/produtos/p1/excluir", "/api/produtos")) {
            mvc.perform(post(rota).with(user("atendente").roles("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/produtos/p1").with(user("atendente").roles("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/produtos/p1").with(user("atendente").roles("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(produtos);
    }

    @Test void adminEGerentePodemGerenciarComCsrf() throws Exception {
        for (String perfil : List.of("ADMIN", "GERENTE")) {
            mvc.perform(get("/produtos").with(user("gestor").roles(perfil)))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Gestão de Produtos")));
            mvc.perform(post("/produtos/p1/status").param("ativo", "false")
                    .with(user("gestor").roles(perfil)).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/produtos"));
        }
        verify(produtos, times(2)).atualizarStatus("p1", false);
    }

    @Test void csrfContinuaObrigatorio() throws Exception {
        mvc.perform(post("/produtos/p1/excluir").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(produtos);
    }

    @Test void tabelaExibeAcoesEStatusMesmoParaRegistroAntigoSemStatus() throws Exception {
        Produto ativo = new Produto();
        ativo.setId("ativo");
        ativo.setNome("Pizza");
        ativo.setTipo("Pizza");
        ativo.setPreco(30.5);
        ativo.setAtivo(true);
        Produto antigo = new Produto();
        antigo.setId("antigo");
        antigo.setNome("Antigo");
        when(produtos.listarProdutos()).thenReturn(List.of(ativo, antigo));
        mvc.perform(get("/produtos").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("R$ 30,50")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INATIVO")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Desativar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ativar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/produtos/editar/ativo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/produtos/antigo/excluir")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("return confirm(")));
    }

    @Test void edicaoAbrePreenchidaESalvaMesmoId() throws Exception {
        Produto produto = new Produto();
        produto.setId("p1");
        produto.setNome("Pizza atual");
        produto.setTipo("Pizza");
        produto.setPreco(30.5);
        produto.setAtivo(true);
        when(produtos.buscarPorId("p1")).thenReturn(produto);
        when(produtos.tiposPermitidos()).thenReturn(List.of("Pizza"));
        mvc.perform(get("/produtos/editar/p1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Pizza atual\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("checked=\"checked\"")));
        mvc.perform(post("/produtos/editar/p1").with(user("admin").roles("ADMIN")).with(csrf())
                .param("id", "outro").param("nome", "Novo nome").param("tipo", "Pizza").param("preco", "40.50").param("_ativo", "on"))
                .andExpect(redirectedUrl("/produtos")).andExpect(flash().attributeExists("sucesso"));
        verify(produtos).salvar(argThat(p -> "p1".equals(p.getId()) && Boolean.FALSE.equals(p.getAtivo())));
    }

    @Test void precoInvalidoReabreFormularioSemSalvar() throws Exception {
        mvc.perform(post("/produtos/editar/p1").with(user("admin").roles("ADMIN")).with(csrf())
                .param("nome", "Pizza").param("tipo", "Pizza").param("preco", "invalido").param("_ativo", "on"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("erro"));
        verify(produtos, never()).salvar(any());
    }

    @Test void pedidoNovoRecebeSomenteListaAtiva() throws Exception {
        mvc.perform(get("/pedidos/novo").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isOk());
        verify(produtos).listarAtivos();
        verify(produtos, never()).listarProdutos();
    }
}
