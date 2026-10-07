package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
        model.addAttribute("podeAdministrarProdutos", temPerfil(authentication, "ADMIN")
                || temPerfil(authentication, "GERENTE"));
        model.addAttribute("podeVisualizarAuditoria", temPerfil(authentication, "ADMIN")
                || temPerfil(authentication, "GERENTE"));
        model.addAttribute("podeGerenciarClientes", temPerfil(authentication, "ATENDENTE"));
        model.addAttribute("podeGerenciarPedidos", temPerfil(authentication, "GERENTE"));
        model.addAttribute("podeAvancarPedidos", temPerfil(authentication, "GERENTE")
                || temPerfil(authentication, "ATENDENTE"));
        model.addAttribute("podeSolicitarCancelamento", temPerfil(authentication, "GERENTE"));
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
    public String listarUsuarios(Model model, Authentication authentication) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("usuarioLogado", authentication.getName());
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
            RedirectAttributes redirectAttributes, Authentication authentication) {
        usuarioService.salvarUsuario(usuario, authentication);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
            return "redirect:/usuarios/editar/" + id;
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/status")
    public String atualizarStatusUsuario(@PathVariable String id, @RequestParam boolean ativo,
                                         Authentication authentication,
                                         RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarStatus(id, ativo, authentication);
            redirectAttributes.addFlashAttribute("sucesso", ativo
                    ? "Usuário ativado com sucesso!" : "Usuário desativado com sucesso!");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/excluir")
    public String excluirUsuario(@PathVariable String id, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            usuarioService.excluirUsuario(id, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário excluído com sucesso.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/usuarios";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model, Authentication authentication) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        model.addAttribute("pedidos", pedidos);
        List<Pedido> naPizzaria = pedidos.stream()
                .filter(pedido -> pedido.getStatus() != null && Set.of("RECEBIDO", "EM_PREPARO", "EM_PREPARACAO", "PRONTO",
                        "PRONTO_PARA_RETIRADA").contains(pedido.getStatus()))
                .toList();
        List<Pedido> emEntrega = pedidos.stream()
                .filter(pedido -> "SAIU_PARA_ENTREGA".equals(pedido.getStatus()))
                .toList();
        List<Pedido> historico = pedidos.stream()
                .filter(pedido -> !naPizzaria.contains(pedido) && !emEntrega.contains(pedido))
                .toList();
        Map<String, List<Pedido>> gruposPedidos = new LinkedHashMap<>();
        gruposPedidos.put("Pedidos na pizzaria", naPizzaria);
        gruposPedidos.put("Pedidos em entrega", emEntrega);
        gruposPedidos.put("Histórico de pedidos", historico);
        model.addAttribute("gruposPedidos", gruposPedidos);
        Map<String, Cliente> clientesPorId = new HashMap<>();
        clienteService.listarClientes().forEach(cliente -> clientesPorId.put(cliente.getId(), cliente));
        model.addAttribute("clientesPorId", clientesPorId);
        Map<String, String> proximosStatus = new HashMap<>();
        pedidos.forEach(pedido -> proximosStatus.put(pedido.getId(), pedidoService.proximoStatus(pedido)));
        model.addAttribute("proximosStatus", proximosStatus);
        Map<String, Boolean> cancelaveisPorId = new HashMap<>();
        pedidos.forEach(pedido -> cancelaveisPorId.put(pedido.getId(), pedidoService.podeCancelar(pedido)));
        model.addAttribute("cancelaveisPorId", cancelaveisPorId);
        model.addAttribute("podeCriarPedido", temPerfil(authentication, "ATENDENTE")
                || temPerfil(authentication, "GERENTE"));
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedido(Model model) {
        prepararFormularioPedido(model);
        model.addAttribute("content", "pedido-form :: content");
        return "layout";
    }

    @PostMapping("/pedidos/autorizar-desconto")
    @ResponseBody
    public ResponseEntity<?> autorizarDescontoNovoPedido(
            @RequestParam List<String> produtoIds,
            @RequestParam List<Integer> quantidades,
            @RequestParam Pedido.TipoPedido tipoPedido,
            @RequestParam(required = false) BigDecimal taxaEntrega,
            @RequestParam Pedido.TipoDesconto tipoDesconto,
            @RequestParam BigDecimal descontoInformado,
            @RequestParam String usuarioGerenteDesconto,
            @RequestParam String senhaGerenteDesconto,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(pedidoService.autorizarDescontoNovoPedido(produtoIds, quantidades, tipoPedido,
                    taxaEntrega, tipoDesconto, descontoInformado, usuarioGerenteDesconto,
                    senhaGerenteDesconto, authentication));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason() == null
                            ? "Não foi possível autorizar o desconto." : e.getReason()));
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhesPedido(@PathVariable String id, Model model) {
        Pedido pedido = pedidoService.buscarPorId(id);
        Cliente cliente = pedido.getClienteId() == null ? null
                : clienteService.buscarPorId(pedido.getClienteId()).orElse(null);
        model.addAttribute("pedido", pedido);
        model.addAttribute("cliente", cliente);
        model.addAttribute("enderecoCliente", enderecoCliente(cliente));
        model.addAttribute("proximoStatus", pedidoService.proximoStatus(pedido));
        model.addAttribute("podeCancelarPedido", pedidoService.podeCancelar(pedido));
        model.addAttribute("content", "pedido-detalhe :: content");
        return "layout";
    }

    @PostMapping("/pedidos/novo")
    public String criarPedidoPelaInterface(
            @RequestParam String clienteId,
            @RequestParam List<String> produtoIds,
            @RequestParam List<Integer> quantidades,
            @RequestParam(required = false) List<String> observacoesItens,
            @RequestParam Pedido.TipoPedido tipoPedido,
            @RequestParam(required = false) String enderecoEntrega,
            @RequestParam(required = false) BigDecimal taxaEntrega,
            @RequestParam Pedido.TipoDesconto tipoDesconto,
            @RequestParam(required = false) BigDecimal descontoInformado,
            @RequestParam(required = false) String usuarioGerenteDesconto,
            @RequestParam(required = false) String senhaGerenteDesconto,
            @RequestParam Pedido.FormaPagamento formaPagamento,
            @RequestParam(required = false) BigDecimal trocoPara,
            @RequestParam(required = false) String observacaoGeral,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (produtoIds.size() != quantidades.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada produto deve ter uma quantidade correspondente.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(clienteId);
        pedido.setTipoPedido(tipoPedido);
        pedido.setEnderecoEntrega(enderecoEntrega);
        pedido.setTaxaEntrega(taxaEntrega);
        pedido.setTipoDesconto(tipoDesconto);
        pedido.setDescontoInformado(descontoInformado);
        pedido.setFormaPagamento(formaPagamento);
        pedido.setTrocoPara(trocoPara);
        pedido.setObservacaoGeral(observacaoGeral);
        List<Pedido.ItemPedido> itens = new ArrayList<>();
        for (int i = 0; i < produtoIds.size(); i++) {
            Pedido.ItemPedido item = new Pedido.ItemPedido();
            item.setProdutoId(produtoIds.get(i));
            item.setQuantidade(quantidades.get(i));
            if (observacoesItens != null && i < observacoesItens.size()) {
                item.setObservacao(observacoesItens.get(i));
            }
            itens.add(item);
        }
        pedido.setItens(itens);

        try {
            pedidoService.criarPedido(pedido, usuarioGerenteDesconto, senhaGerenteDesconto, authentication);
        } catch (ResponseStatusException e) {
            prepararFormularioPedido(model);
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
        try {
            pedidoService.atualizarStatus(id, status, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Status do pedido atualizado.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDescontoPedido(@PathVariable String id,
                                        @RequestParam Pedido.TipoDesconto tipoDesconto,
                                        @RequestParam BigDecimal descontoInformado,
                                        Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.aplicarDesconto(id, tipoDesconto, descontoInformado, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto aplicado.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelamento")
    public String cancelarPedido(@PathVariable String id, @RequestParam String justificativa,
                                 @RequestParam String usuarioGerente,
                                 @RequestParam String senhaGerente,
                                 Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelar(id, justificativa, usuarioGerente, senhaGerente, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/excluir")
    public String excluirPedido(@PathVariable String id, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            pedidoService.excluir(id, authentication);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido excluído definitivamente.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
            return "redirect:/pedidos/" + id;
        }
        return "redirect:/pedidos";
    }

    private void prepararFormularioPedido(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("tiposPedido", Pedido.TipoPedido.values());
        model.addAttribute("tiposDesconto", List.of(Pedido.TipoDesconto.SEM_DESCONTO, Pedido.TipoDesconto.PERCENTUAL));
        model.addAttribute("formasPagamento", Pedido.FormaPagamento.values());
    }

    private boolean temPerfil(Authentication authentication, String perfil) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + perfil).equals(authority.getAuthority()));
    }

    private String enderecoCliente(Cliente cliente) {
        if (cliente == null) return null;
        List<String> partes = new ArrayList<>();
        adicionarParteEndereco(partes, cliente.getLogradouro());
        adicionarParteEndereco(partes, cliente.getNumero());
        adicionarParteEndereco(partes, cliente.getComplemento());
        adicionarParteEndereco(partes, cliente.getBairro());
        adicionarParteEndereco(partes, cliente.getCidade());
        adicionarParteEndereco(partes, cliente.getEstado());
        adicionarParteEndereco(partes, cliente.getCep());
        if (!partes.isEmpty()) return String.join(", ", partes);
        return cliente.getEndereco();
    }

    private void adicionarParteEndereco(List<String> partes, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(valor.trim());
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("produto", new Produto());
        model.addAttribute("tiposProduto", produtoService.tiposPermitidos());
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    @PostMapping("/produtos")
    public String salvarProdutoPelaInterface(@ModelAttribute("produto") Produto produto,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return formularioProdutoComErro(produto, model, "Informe valores válidos para preço e status.");
        }
        try {
            produtoService.novoProduto(produto);
        } catch (ResponseStatusException e) {
            return formularioProdutoComErro(produto, model, e.getReason());
        }
        redirectAttributes.addFlashAttribute("sucesso", "Produto cadastrado com sucesso.");
        return "redirect:/produtos";
    }

    @GetMapping("/produtos/editar/{id}")
    public String editarProdutoForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("produto", produtoService.buscarPorId(id));
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
            return "redirect:/produtos";
        }
        model.addAttribute("tiposProduto", produtoService.tiposPermitidos());
        model.addAttribute("content", "produtos/produto-form :: content");
        return "layout";
    }

    @PostMapping("/produtos/editar/{id}")
    public String atualizarProduto(@PathVariable String id, @ModelAttribute("produto") Produto produto,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        produto.setId(id);
        if (bindingResult.hasErrors()) {
            return formularioProdutoComErro(produto, model, "Informe valores válidos para preço e status.");
        }
        try {
            produtoService.salvar(produto);
        } catch (ResponseStatusException e) {
            return formularioProdutoComErro(produto, model, e.getReason());
        }
        redirectAttributes.addFlashAttribute("sucesso", "Produto atualizado com sucesso.");
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/{id}/status")
    public String atualizarStatusProduto(@PathVariable String id, @RequestParam boolean ativo,
            RedirectAttributes redirectAttributes) {
        try {
            produtoService.atualizarStatus(id, ativo);
            redirectAttributes.addFlashAttribute("sucesso", ativo
                    ? "Produto ativado com sucesso." : "Produto desativado com sucesso.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/{id}/excluir")
    public String excluirProduto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            produtoService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Produto excluído com sucesso.");
        } catch (ResponseStatusException e) {
            redirectAttributes.addFlashAttribute("erro", e.getReason());
        }
        return "redirect:/produtos";
    }

    private String formularioProdutoComErro(Produto produto, Model model, String mensagem) {
        model.addAttribute("erro", mensagem);
        model.addAttribute("tiposProduto", produtoService.tiposPermitidos());
        if (produto.getId() == null) {
            model.addAttribute("produtos", produtoService.listarProdutos());
            model.addAttribute("abrirCadastro", true);
            model.addAttribute("content", "produtos/lista :: content");
        } else {
            model.addAttribute("content", "produtos/produto-form :: content");
        }
        return "layout";
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
    public String auditoria(@RequestParam(defaultValue = "") String usuario,
            @RequestParam(defaultValue = "") String operacao, @RequestParam(defaultValue = "") String entidade,
            @RequestParam(defaultValue = "") String dataInicial, @RequestParam(defaultValue = "") String dataFinal,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("usuarioFiltro", usuario);
        model.addAttribute("operacaoFiltro", operacao);
        model.addAttribute("entidadeFiltro", entidade);
        model.addAttribute("dataInicial", dataInicial);
        model.addAttribute("dataFinal", dataFinal);
        model.addAttribute("operacoes", auditoriaService.operacoesDisponiveis());
        org.springframework.data.domain.Page<AuditoriaService.RegistroConsulta> resultado;
        try {
            resultado = auditoriaService.consultar(usuario, operacao, entidade, dataInicial, dataFinal, pagina);
        } catch (ResponseStatusException e) {
            model.addAttribute("erro", e.getReason());
            resultado = org.springframework.data.domain.Page.empty();
        }
        model.addAttribute("paginaAuditoria", resultado);
        model.addAttribute("auditorias", resultado.getContent());
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
