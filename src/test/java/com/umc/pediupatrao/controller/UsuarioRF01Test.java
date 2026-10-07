package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.repository.*;
import com.umc.pediupatrao.service.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, UsuarioController.class, ClienteController.class, PedidoController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioService.class, ClienteService.class, PedidoService.class})
class UsuarioRF01Test {
    @Autowired MockMvc mvc;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired PasswordEncoder encoder;
    @Autowired UsuarioService usuarioService;
    @Autowired UsuarioDetailsService details;
    @Autowired ClienteService clienteService;
    @Autowired PedidoService pedidoService;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean ClienteRepository clientes;
    @MockitoBean PedidoRepository pedidos;
    @MockitoBean ProdutoRepository produtos;
    @MockitoBean AuditoriaService auditoria;
    @MockitoBean ProdutoService produtoService;

    private Authentication autenticacao(String perfil) {
        return UsernamePasswordAuthenticationToken.authenticated(perfil.toLowerCase(), "",
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
    }

    private Usuario usuario(String id, String nome, String perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(nome);
        usuario.setRole(perfil);
        usuario.setPassword(encoder.encode("senha"));
        usuario.setAtivo(true);
        return usuario;
    }

    private String cadastro(String perfil) {
        return "{\"username\":\"novo\",\"password\":\"senha\",\"role\":\"" + perfil + "\"}";
    }

    @Test void autenticaExatamenteOsTresPerfisComAuthoritiesCorretas() {
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            String nome = perfil.toLowerCase();
            when(usuarios.findByUsername(nome)).thenReturn(Optional.of(usuario(nome, nome, perfil)));
            Authentication autenticado = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(nome, "senha"));
            assertTrue(autenticado.isAuthenticated());
            assertEquals(List.of(new SimpleGrantedAuthority("ROLE_" + perfil)),
                    List.copyOf(autenticado.getAuthorities()));
        }
    }

    @Test void perfilInvalidoOuAusenteEContaInativaNaoAutenticam() {
        Usuario invalido = usuario("u1", "invalido", "USER");
        when(usuarios.findByUsername("invalido")).thenReturn(Optional.of(invalido));
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername("invalido"));
        invalido.setRole(null);
        assertThrows(UsernameNotFoundException.class, () -> details.loadUserByUsername("invalido"));
        Usuario inativo = usuario("u2", "inativo", "ADMIN");
        inativo.setAtivo(false);
        when(usuarios.findByUsername("inativo")).thenReturn(Optional.of(inativo));
        assertThrows(org.springframework.security.authentication.DisabledException.class,
                () -> authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("inativo", "senha")));
    }

    @Test void adminCriaUsuariosComCadaPerfilEAtribuiPerfilPelaApi() throws Exception {
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(cadastro(perfil)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(perfil))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
        Usuario existente = usuario("u1", "novo", "ATENDENTE");
        when(usuarios.findById("u1")).thenReturn(Optional.of(existente));
        mvc.perform(put("/api/usuarios/u1").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cadastro("GERENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("GERENTE"));
        assertEquals("GERENTE", existente.getRole());
        verify(usuarios, times(4)).save(any());
    }

    @Test void gerenteEAtendenteNaoAdministramNemElevamProprioPerfilPorHttp() throws Exception {
        for (String perfil : List.of("GERENTE", "ATENDENTE")) {
            mvc.perform(get("/api/usuarios").with(user("comum").roles(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/usuarios/novo").with(user("comum").roles(perfil)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/usuarios").with(user("comum").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(cadastro("ADMIN")))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/usuarios/proprio").with(user("comum").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(cadastro("ADMIN")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/salvar").with(user("comum").roles(perfil)).with(csrf())
                    .param("username", "comum").param("password", "senha").param("role", "ADMIN"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/usuarios/u1/status").with(user("comum").roles(perfil))
                    .with(csrf()).param("ativo", "false")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/usuarios/u1").with(user("comum").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(usuarios);
    }

    @Test void serviceTambemBloqueiaCriacaoEAutoelevacaoSemAdmin() {
        for (Authentication auth : new Authentication[]{null, autenticacao("GERENTE"), autenticacao("ATENDENTE")}) {
            Usuario dados = usuario(null, "comum", "ADMIN");
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> usuarioService.salvarUsuario(dados, auth)).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> usuarioService.atualizarUsuario("proprio", dados, auth)).getStatusCode());
        }
        verifyNoInteractions(usuarios);
    }

    @Test void cadastroNaoPermitePerfilExtraNemSobrescreverContaPorId() throws Exception {
        for (String corpo : List.of(cadastro("USER"), cadastro("ADMIN").replace("{", "{\"id\":\"admin-existente\","))) {
            mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(usuarios);
    }

    @Test void cadastroNaoEPublicoEOperacoesExigemCsrf() throws Exception {
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cadastro("ADMIN"))).andExpect(status().is3xxRedirection());
        mvc.perform(post("/usuarios/salvar").with(csrf()).param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(cadastro("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").with(user("atendente").roles("ATENDENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").with(user("gerente").roles("GERENTE"))
                .param("status", "EM_PREPARO")).andExpect(status().isForbidden());
        verifyNoInteractions(usuarios, clientes, pedidos);
    }

    @Test void clientesPermitemConsultaAoGerenteECadastroAlteracaoSomenteAoAtendente() throws Exception {
        when(clientes.findAll()).thenReturn(List.of());
        when(clientes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(clientes.existsById("c1")).thenReturn(true);
        when(clientes.findById("c1")).thenReturn(Optional.of(new Cliente()));
        for (String perfil : List.of("GERENTE", "ATENDENTE")) {
            mvc.perform(get("/api/clientes").with(user("operador").roles(perfil))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/clientes").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            var criar = post("/api/clientes").with(user("operador").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}");
            var alterar = put("/api/clientes/c1").with(user("operador").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Alterado\"}");
            mvc.perform(criar).andExpect(perfil.equals("ATENDENTE") ? status().isCreated() : status().isForbidden());
            mvc.perform(alterar).andExpect(perfil.equals("ATENDENTE") ? status().isOk() : status().isForbidden());
            mvc.perform(delete("/api/clientes/c1").with(user("operador").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verify(clientes, times(2)).save(any());
        verify(clientes, never()).deleteById(anyString());
        for (String perfil : List.of("ADMIN", "GERENTE")) {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> clienteService.salvar(new Cliente(), autenticacao(perfil))).getStatusCode());
        }
    }

    @Test void adminConsultaPedidosMasNaoCriaNemAvancaEtapas() throws Exception {
        when(pedidos.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/pedidos").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").with(user("admin").roles("ADMIN")).with(csrf())
                .param("status", "EM_PREPARO")).andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/status").with(user("admin").roles("ADMIN")).with(csrf())
                .param("status", "EM_PREPARO")).andExpect(status().isForbidden());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> pedidoService.criarPedido(new Pedido(), autenticacao("ADMIN"))).getStatusCode());
        verify(pedidos, never()).save(any());
    }

    @Test void paginasThymeleafRenderizamComRotasCsrfEAcoesCompativeisComCadaPerfil() throws Exception {
        Cliente cliente = new Cliente(); cliente.setId("c1"); cliente.setNome("Cliente");
        when(clientes.findAll()).thenReturn(List.of(cliente));
        when(clientes.findById("c1")).thenReturn(Optional.of(cliente));
        Pedido pedido = new Pedido(); pedido.setId("p1"); pedido.setClienteId("c1");
        pedido.setStatus("PRONTO"); pedido.setTipoPedido(Pedido.TipoPedido.RETIRADA);
        pedido.setFormaPagamento(Pedido.FormaPagamento.PIX);
        Pedido.ItemPedido item = new Pedido.ItemPedido(); item.setProdutoId("prod1"); item.setQuantidade(1);
        item.setNomeProduto("Pizza"); item.setPrecoUnitario(new java.math.BigDecimal("30.00"));
        pedido.setItens(List.of(item));
        when(pedidos.findAll()).thenReturn(List.of(pedido));
        when(pedidos.findById("p1")).thenReturn(Optional.of(pedido));
        when(usuarios.findAll()).thenReturn(List.of(usuario("u1", "outro", "ATENDENTE")));
        when(usuarios.findById("u1")).thenReturn(Optional.of(usuario("u1", "outro", "ATENDENTE")));
        when(auditoria.consultar("", "", "", "", "", 0)).thenReturn(org.springframework.data.domain.Page.empty());
        when(auditoria.operacoesDisponiveis()).thenReturn(java.util.Map.of());
        for (String perfil : List.of("ADMIN", "GERENTE", "ATENDENTE")) {
            for (String rota : List.of("/pedidos", "/pedidos/p1")) {
                String html = mvc.perform(get(rota).with(user("operador").roles(perfil)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertTrue(html.contains("name=\"_csrf\""));
                assertEquals(!perfil.equals("ADMIN"), html.contains("action=\"/pedidos/p1/status\""));
                assertEquals(perfil.equals("GERENTE"), html.contains("action=\"/pedidos/p1/cancelamento\""));
                assertEquals(!perfil.equals("ADMIN"), html.contains("href=\"/clientes\""));
                assertEquals(perfil.equals("ADMIN"), html.contains("href=\"/usuarios\""));
                assertEquals(!perfil.equals("ATENDENTE"), html.contains("href=\"/auditoria\""));
                if (rota.equals("/pedidos/p1")) {
                    assertEquals(perfil.equals("GERENTE"), html.contains("action=\"/pedidos/p1/desconto\""));
                }
            }
            List<String> rotas = switch (perfil) {
                case "ADMIN" -> List.of("/usuarios", "/usuarios/novo", "/usuarios/editar/u1", "/auditoria");
                case "GERENTE" -> List.of("/pedidos/novo", "/clientes", "/auditoria");
                default -> List.of("/pedidos/novo", "/clientes", "/clientes/novo", "/clientes/editar/c1");
            };
            for (String rota : rotas) {
                String html = mvc.perform(get(rota).with(user("operador").roles(perfil)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertTrue(html.contains("name=\"_csrf\""), rota);
                if (rota.equals("/pedidos/novo")) {
                    assertEquals(perfil.equals("GERENTE"), html.contains("data-bs-target=\"#autorizarDescontoModal\""));
                }
                if (rota.equals("/clientes")) {
                    assertEquals(perfil.equals("ATENDENTE"), html.contains("href=\"/clientes/novo\""));
                    assertTrue(html.contains("data-pode-editar=\"" + perfil.equals("ATENDENTE") + "\""));
                }
            }
        }
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test void interfacePreservaMensagemDeErroERedirecionamentoSemSalvarTransicaoInvalida() throws Exception {
        Pedido pedido = new Pedido(); pedido.setId("p1"); pedido.setStatus("RECEBIDO");
        when(pedidos.findById("p1")).thenReturn(Optional.of(pedido));
        mvc.perform(post("/pedidos/p1/status").with(user("operador").roles("ATENDENTE")).with(csrf())
                .param("status", "FINALIZADO")).andExpect(redirectedUrl("/pedidos"))
                .andExpect(flash().attributeExists("erro"));
        verify(pedidos, never()).save(any());
        verifyNoInteractions(auditoria);
        mvc.perform(get("/usuarios/editar/inexistente").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/usuarios")).andExpect(flash().attributeExists("erro"));
    }

    @Test void protecoesDaPropriaContaEDoUltimoAdminContinuamAtivas() {
        Usuario admin = usuario("a1", "admin", "ADMIN");
        when(usuarios.findById("a1")).thenReturn(Optional.of(admin));
        when(usuarios.findAll()).thenReturn(List.of(admin));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> usuarioService.atualizarStatus("a1", false, autenticacao("ADMIN"))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> usuarioService.excluirUsuario("a1", autenticacao("ADMIN"))).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> usuarioService.atualizarUsuario("a1", usuario(null, "admin", "ATENDENTE"),
                        autenticacao("ADMIN"))).getStatusCode());
        verify(usuarios, never()).save(any());
        verify(usuarios, never()).delete(any());
    }
}
