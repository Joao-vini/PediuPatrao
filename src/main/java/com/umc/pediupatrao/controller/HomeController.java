package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuditoriaService auditoriaService;

    @ModelAttribute
    public void prepararPermissoesDaInterface(Model model, Authentication authentication) {
        model.addAttribute("podeAdministrarUsuarios", temPerfil(authentication, "ADMIN"));
        model.addAttribute("podeVisualizarAuditoria", temPerfil(authentication, "ADMIN")
                || temPerfil(authentication, "GERENTE"));
        model.addAttribute("podeGerenciarClientes", temPerfil(authentication, "ATENDENTE"));
        model.addAttribute("podeGerenciarPedidos", temPerfil(authentication, "GERENTE"));
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null);
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        usuarioService.salvarUsuario(usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        usuarioService.atualizarUsuario(id, usuario);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        usuarioService.deletarUsuario(id);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        return "redirect:/usuarios";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model, Authentication authentication) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("podeCriarPedido", temPerfil(authentication, "ATENDENTE")
                || temPerfil(authentication, "GERENTE"));
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedido(Model model, Authentication authentication) {
        prepararFormularioPedido(model, authentication);
        model.addAttribute("content", "pedido-form :: content");
        return "layout";
    }

    @PostMapping("/pedidos/novo")
    public String criarPedidoPelaInterface(
            @RequestParam String clienteId,
            @RequestParam List<String> produtoIds,
            @RequestParam List<Integer> quantidades,
            @RequestParam(required = false) BigDecimal descontoPercentual,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (produtoIds.size() != quantidades.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada produto deve ter uma quantidade correspondente.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(clienteId);
        pedido.setDescontoPercentual(descontoPercentual);
        List<Pedido.ItemPedido> itens = new ArrayList<>();
        for (int i = 0; i < produtoIds.size(); i++) {
            Pedido.ItemPedido item = new Pedido.ItemPedido();
            item.setProdutoId(produtoIds.get(i));
            item.setQuantidade(quantidades.get(i));
            itens.add(item);
        }
        pedido.setItens(itens);

        try {
            pedidoService.criarPedido(pedido, authentication);
        } catch (ResponseStatusException e) {
            prepararFormularioPedido(model, authentication);
            model.addAttribute("erro", e.getReason());
            model.addAttribute("content", "pedido-form :: content");
            return "layout";
        }

        redirectAttributes.addFlashAttribute("sucesso", "Pedido criado com sucesso!");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/status")
    public String atualizarStatusPedido(@PathVariable String id, @RequestParam String status,
                                        Authentication authentication, RedirectAttributes redirectAttributes) {
        pedidoService.atualizarStatus(id, status, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Status do pedido atualizado.");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDescontoPedido(@PathVariable String id, @RequestParam BigDecimal percentual,
                                        Authentication authentication, RedirectAttributes redirectAttributes) {
        pedidoService.aplicarDesconto(id, percentual, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Desconto aplicado.");
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/cancelamento")
    public String cancelarPedido(@PathVariable String id, @RequestParam String justificativa,
                                 Authentication authentication, RedirectAttributes redirectAttributes) {
        pedidoService.cancelar(id, justificativa, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        return "redirect:/pedidos";
    }

    private void prepararFormularioPedido(Model model, Authentication authentication) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("podeAplicarDesconto", temPerfil(authentication, "GERENTE"));
    }

    private boolean temPerfil(Authentication authentication, String perfil) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + perfil).equals(authority.getAuthority()));
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    @PostMapping("/produtos")
    public String salvarProdutoPelaInterface(@ModelAttribute Produto produto,
                                              RedirectAttributes redirectAttributes) {
        produtoService.salvar(produto);
        redirectAttributes.addFlashAttribute("sucesso", "Produto cadastrado com sucesso.");
        return "redirect:/produtos";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        clienteService.salvar(cliente, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        clienteService.salvar(cliente, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/auditoria")
    public String auditoria(Model model) {
        List<Auditoria> registros = auditoriaService.listar();
        model.addAttribute("auditorias", registros);
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }

    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
