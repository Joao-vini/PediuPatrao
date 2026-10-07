package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal CEM = new BigDecimal("100");
    // Mantém o limite mais restritivo já usado pelo RF04; portanto também impede valores acima de 100%.
    private static final BigDecimal PERCENTUAL_MAXIMO = new BigDecimal("20");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, UsuarioRepository usuarioRepository,
                         AuditoriaService auditoriaService, PasswordEncoder passwordEncoder) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.passwordEncoder = passwordEncoder;
    }

    public Pedido criarPedido(Pedido pedido, Authentication authentication) {
        return criarPedido(pedido, null, null, authentication);
    }

    public Pedido criarPedido(Pedido pedido, String usuarioGerente, String senhaGerente,
                              Authentication authentication) {
        if (pedido == null) throw erro(HttpStatus.BAD_REQUEST, "Os dados do pedido são obrigatórios.");
        validarAutenticacao(authentication);
        // Protege também a criação via API, inclusive o campo percentual da API antiga.
        exigirPerfilOperacional(authentication);
        if ((pedido.getTipoDesconto() != null && pedido.getTipoDesconto() != Pedido.TipoDesconto.SEM_DESCONTO)
                || (pedido.getDescontoPercentual() != null && pedido.getDescontoPercentual().signum() != 0)) {
            exigirGerente(authentication);
        }

        Cliente cliente = pedido.getClienteId() == null ? null
                : clienteRepository.findById(pedido.getClienteId()).orElse(null);
        if (cliente == null) throw erro(HttpStatus.BAD_REQUEST, "Cliente não encontrado.");
        if (pedido.getTipoPedido() == null) throw erro(HttpStatus.BAD_REQUEST, "Selecione o tipo do pedido.");
        if (pedido.getFormaPagamento() == null) throw erro(HttpStatus.BAD_REQUEST, "Selecione a forma de pagamento.");
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "O pedido deve possuir ao menos um item.");
        }

        Pedido.TipoPedido tipo = pedido.getTipoPedido();
        BigDecimal taxaEntrega = valorOuZero(pedido.getTaxaEntrega());
        if (taxaEntrega.signum() < 0) throw erro(HttpStatus.BAD_REQUEST, "A taxa de entrega não pode ser negativa.");
        if (tipo != Pedido.TipoPedido.ENTREGA) taxaEntrega = ZERO;
        String endereco = limpar(pedido.getEnderecoEntrega());
        if (tipo == Pedido.TipoPedido.ENTREGA) {
            if (endereco == null) endereco = enderecoCliente(cliente);
            if (endereco == null) throw erro(HttpStatus.BAD_REQUEST, "Informe o endereço para entrega.");
        } else {
            endereco = null;
        }

        Usuario responsavel = usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));

        ItensCalculados itensComSubtotal = calcularItens(pedido.getItens());
        List<Pedido.ItemPedido> itensCalculados = itensComSubtotal.itens();
        BigDecimal subtotal = itensComSubtotal.subtotal();

        Pedido.TipoDesconto tipoDesconto = pedido.getTipoDesconto();
        BigDecimal descontoInformado = pedido.getDescontoInformado();
        // Mantém compatibilidade com clientes da API antiga que enviam descontoPercentual.
        if (tipoDesconto == null && pedido.getDescontoPercentual() != null) {
            tipoDesconto = Pedido.TipoDesconto.PERCENTUAL;
            descontoInformado = pedido.getDescontoPercentual();
        }
        if (tipoDesconto == null) tipoDesconto = Pedido.TipoDesconto.SEM_DESCONTO;
        BigDecimal base = subtotal.add(taxaEntrega);
        BigDecimal desconto = calcularDesconto(tipoDesconto, descontoInformado, base);
        Usuario gerenteAutorizador = null;
        if (desconto.signum() > 0) {
            gerenteAutorizador = validarCredenciaisGerente(usuarioGerente, senhaGerente);
        }
        BigDecimal total = base.subtract(desconto).setScale(2, RoundingMode.HALF_UP);

        BigDecimal trocoPara = pedido.getFormaPagamento() == Pedido.FormaPagamento.DINHEIRO
                ? pedido.getTrocoPara() : null;
        BigDecimal valorTroco = null;
        if (pedido.getFormaPagamento() == Pedido.FormaPagamento.DINHEIRO) {
            if (trocoPara == null) {
                throw erro(HttpStatus.BAD_REQUEST, "Informe o valor recebido em dinheiro para calcular o troco.");
            }
            trocoPara = arredondar(trocoPara);
            if (trocoPara.compareTo(total) < 0) {
                throw erro(HttpStatus.BAD_REQUEST, "O valor informado para troco deve ser igual ou maior que o total.");
            }
            valorTroco = trocoPara.subtract(total).setScale(2, RoundingMode.HALF_UP);
        }

        pedido.setId(null);
        pedido.setTipoPizza(null);
        pedido.setSabor(null);
        pedido.setQuantidade(0);
        pedido.setClienteNome(cliente.getNome());
        pedido.setClienteTelefone(cliente.getTelefone());
        pedido.setEnderecoEntrega(endereco);
        pedido.setItens(itensCalculados);
        pedido.setSubtotalProdutos(subtotal.setScale(2, RoundingMode.HALF_UP));
        pedido.setTaxaEntrega(taxaEntrega.setScale(2, RoundingMode.HALF_UP));
        pedido.setTipoDesconto(tipoDesconto);
        pedido.setDescontoInformado(tipoDesconto == Pedido.TipoDesconto.SEM_DESCONTO
                ? ZERO : arredondar(descontoInformado));
        pedido.setDescontoPercentual(tipoDesconto == Pedido.TipoDesconto.PERCENTUAL
                ? pedido.getDescontoInformado() : ZERO);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(total);
        pedido.setTrocoPara(trocoPara);
        pedido.setValorTroco(valorTroco);
        pedido.setObservacaoGeral(limpar(pedido.getObservacaoGeral()));
        pedido.setUsuarioResponsavelId(responsavel.getId());
        pedido.setUsuarioResponsavelUsername(responsavel.getUsername());
        pedido.setDataHora(Instant.now());
        pedido.setStatus("RECEBIDO");
        // Dados de saída nunca são aceitos na criação, mesmo se enviados pela API.
        pedido.setDataHoraSaida(null);
        pedido.setUsuarioSaidaId(null);
        pedido.setUsuarioSaidaUsername(null);
        pedido.setMotivoCancelamento(null);
        pedido.setCanceladoEm(null);
        pedido.setCanceladoPorId(null);
        pedido.setCanceladoPorUsername(null);

        Pedido salvo = pedidoRepository.save(pedido);
        Map<String, String> novo = new LinkedHashMap<>();
        novo.put("status", salvo.getStatus());
        novo.put("tipoPedido", tipo.name());
        novo.put("cliente", cliente.getNome() == null ? "" : cliente.getNome());
        novo.put("subtotal", subtotal.toPlainString());
        novo.put("taxaEntrega", taxaEntrega.toPlainString());
        novo.put("valorTotal", total.toPlainString());
        novo.put("operador", responsavel.getUsername());
        novo.put("operadorId", responsavel.getId() == null ? "" : responsavel.getId());
        if (gerenteAutorizador != null) {
            novo.put("gerenteAutorizador", gerenteAutorizador.getUsername());
            novo.put("gerenteAutorizadorId", gerenteAutorizador.getId() == null ? "" : gerenteAutorizador.getId());
        }
        auditoriaService.registrar(authentication, "PEDIDO_CRIADO", "PEDIDO", salvo.getId(), Map.of(), novo, null);
        if (desconto.signum() > 0) {
            auditoriaService.registrar(authentication, "DESCONTO_APLICADO", "PEDIDO", salvo.getId(),
                    Map.of("tipoDesconto", Pedido.TipoDesconto.SEM_DESCONTO.name(),
                            "valorInformado", ZERO.toPlainString(), "valorDesconto", ZERO.toPlainString(),
                            "valorTotal", subtotal.add(taxaEntrega).toPlainString()),
                    Map.of("operador", responsavel.getUsername(),
                            "operadorId", responsavel.getId() == null ? "" : responsavel.getId(),
                            "gerenteAutorizador", gerenteAutorizador.getUsername(),
                            "gerenteAutorizadorId", gerenteAutorizador.getId() == null ? "" : gerenteAutorizador.getId(),
                            "tipoDesconto", tipoDesconto.name(),
                            "valorInformado", descontoInformado.toPlainString(),
                            "valorDesconto", desconto.toPlainString(),
                            "valorTotal", total.toPlainString()), null);
        }
        return salvo;
    }

    public Map<String, BigDecimal> autorizarDescontoNovoPedido(
            List<String> produtoIds, List<Integer> quantidades, Pedido.TipoPedido tipoPedido,
            BigDecimal taxaEntrega, Pedido.TipoDesconto tipoDesconto, BigDecimal valorDesconto,
            String usuarioGerente, String senhaGerente, Authentication authentication) {
        exigirGerente(authentication);
        validarCredenciaisGerente(usuarioGerente, senhaGerente);
        if (produtoIds == null || quantidades == null || produtoIds.isEmpty()
                || produtoIds.size() != quantidades.size()) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe produtos e quantidades válidos.");
        }
        if (tipoPedido == null) throw erro(HttpStatus.BAD_REQUEST, "Selecione o tipo do pedido.");
        BigDecimal taxa = valorOuZero(taxaEntrega);
        if (taxa.signum() < 0) throw erro(HttpStatus.BAD_REQUEST, "A taxa de entrega não pode ser negativa.");
        if (tipoPedido != Pedido.TipoPedido.ENTREGA) taxa = ZERO;

        List<Pedido.ItemPedido> itensSolicitados = new ArrayList<>();
        for (int i = 0; i < produtoIds.size(); i++) {
            Pedido.ItemPedido item = new Pedido.ItemPedido();
            item.setProdutoId(produtoIds.get(i));
            item.setQuantidade(quantidades.get(i));
            itensSolicitados.add(item);
        }
        BigDecimal subtotal = calcularItens(itensSolicitados).subtotal();
        BigDecimal base = subtotal.add(taxa);
        BigDecimal desconto = calcularDesconto(tipoDesconto, valorDesconto, base);
        return Map.of("subtotalProdutos", subtotal, "taxaEntrega", taxa,
                "valorDesconto", desconto, "valorTotal", base.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    private ItensCalculados calcularItens(List<Pedido.ItemPedido> itensRecebidos) {
        List<Pedido.ItemPedido> itens = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        for (Pedido.ItemPedido itemRecebido : itensRecebidos) {
            if (itemRecebido == null || itemRecebido.getProdutoId() == null
                    || itemRecebido.getProdutoId().isBlank() || itemRecebido.getQuantidade() <= 0) {
                throw erro(HttpStatus.BAD_REQUEST, "Cada item deve informar produto e quantidade positiva.");
            }
            Produto produto = produtoRepository.findById(itemRecebido.getProdutoId())
                    .orElseThrow(() -> erro(HttpStatus.BAD_REQUEST,
                            "Produto não encontrado: " + itemRecebido.getProdutoId()));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw erro(HttpStatus.BAD_REQUEST, "Produto inativo não pode ser adicionado a um novo pedido.");
            }
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0) {
                throw erro(HttpStatus.BAD_REQUEST, "O produto não possui um preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            Pedido.ItemPedido item = new Pedido.ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(itemRecebido.getQuantidade());
            item.setPrecoUnitario(preco);
            item.setObservacao(limpar(itemRecebido.getObservacao()));
            itens.add(item);
            subtotal = subtotal.add(preco.multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        return new ItensCalculados(itens, subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    private record ItensCalculados(List<Pedido.ItemPedido> itens, BigDecimal subtotal) { }

    public List<Pedido> listarPedidos() { return pedidoRepository.findAll(); }

    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    public Pedido atualizarStatus(String id, String status, Authentication authentication) {
        validarAutenticacao(authentication);
        exigirPerfilOperacional(authentication);
        Pedido pedido = buscarPorId(id);
        String atual = pedido.getStatus();
        String proximo = proximoStatus(pedido);
        if (proximo == null && dependeDoTipo(atual) && pedido.getTipoPedido() == null) {
            throw erro(HttpStatus.BAD_REQUEST,
                    "Não é possível avançar este pedido antigo porque o tipo do pedido não foi registrado.");
        }
        if (proximo == null || !proximo.equals(status)) {
            throw erro(HttpStatus.BAD_REQUEST, "Transição de status inválida para este pedido.");
        }
        if ("SAIU_PARA_ENTREGA".equals(proximo) || "RETIRADO".equals(proximo)) {
            Usuario responsavel = usuarioRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));
            pedido.setDataHoraSaida(Instant.now());
            pedido.setUsuarioSaidaId(responsavel.getId());
            pedido.setUsuarioSaidaUsername(authentication.getName());
        }
        pedido.setStatus(proximo);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "STATUS_PEDIDO_ALTERADO", "PEDIDO", id,
                Map.of("status", atual), Map.of("status", proximo), null);
        return salvo;
    }

    public String proximoStatus(Pedido pedido) {
        if (pedido == null || pedido.getStatus() == null) return null;
        return switch (pedido.getStatus()) {
            case "RECEBIDO" -> "EM_PREPARO";
            case "EM_PREPARACAO", "EM_PREPARO" -> "PRONTO";
            case "PRONTO" -> pedido.getTipoPedido() == null ? null
                    : (pedido.getTipoPedido() == Pedido.TipoPedido.ENTREGA ? "SAIU_PARA_ENTREGA"
                    : "RETIRADO"); // RETIRADA e o tipo já existente BALCAO seguem retirada.
            // O estado legado já identifica retirada, mesmo se o tipo estiver ausente.
            case "PRONTO_PARA_RETIRADA" -> pedido.getTipoPedido() == Pedido.TipoPedido.ENTREGA ? null : "RETIRADO";
            case "SAIU_PARA_ENTREGA" -> pedido.getTipoPedido() == null
                    || pedido.getTipoPedido() == Pedido.TipoPedido.ENTREGA ? "FINALIZADO" : null;
            case "RETIRADO" -> pedido.getTipoPedido() == Pedido.TipoPedido.ENTREGA ? null : "FINALIZADO";
            default -> null;
        };
    }

    public boolean podeCancelar(Pedido pedido) {
        if (pedido == null || pedido.getStatus() == null) return false;
        return List.of("RECEBIDO", "EM_PREPARACAO", "EM_PREPARO", "PRONTO", "PRONTO_PARA_RETIRADA")
                .contains(pedido.getStatus());
    }

    private boolean dependeDoTipo(String status) {
        return "PRONTO".equals(status);
    }

    public void excluir(String id, Authentication authentication) {
        exigirGerente(authentication);
        Pedido pedido = buscarPorId(id);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("status", pedido.getStatus() == null ? "" : pedido.getStatus());
        valores.put("clienteId", pedido.getClienteId() == null ? "" : pedido.getClienteId());
        valores.put("valorTotal", pedido.getValorTotal() == null ? "" : pedido.getValorTotal().toPlainString());
        // A auditoria fica em uma coleção independente e é persistida antes de remover o pedido.
        auditoriaService.registrar(authentication, "PEDIDO_EXCLUIDO", "PEDIDO", pedido.getId(),
                Map.of(), valores, null);
        pedidoRepository.delete(pedido);
    }

    public Pedido aplicarDesconto(String id, BigDecimal percentual, Authentication authentication) {
        return aplicarDesconto(id, Pedido.TipoDesconto.PERCENTUAL, percentual, authentication);
    }

    public Pedido aplicarDesconto(String id, Pedido.TipoDesconto tipo, BigDecimal valor,
                                  Authentication authentication) {
        exigirGerente(authentication);
        Pedido pedido = buscarPorId(id);
        exigirAntesDaSaida(pedido);
        BigDecimal subtotal = obterSubtotal(pedido);
        BigDecimal taxa = valorOuZero(pedido.getTaxaEntrega());
        BigDecimal base = subtotal.add(taxa);
        Map<String, String> anteriores = snapshotDesconto(pedido);
        BigDecimal desconto = calcularDesconto(tipo, valor, base);
        pedido.setTipoDesconto(tipo);
        pedido.setDescontoInformado(tipo == Pedido.TipoDesconto.SEM_DESCONTO ? ZERO : arredondar(valor));
        pedido.setDescontoPercentual(tipo == Pedido.TipoDesconto.PERCENTUAL
                ? pedido.getDescontoInformado() : ZERO);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(base.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
        atualizarTroco(pedido);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "DESCONTO_APLICADO", "PEDIDO", id,
                anteriores, snapshotDesconto(salvo), null);
        return salvo;
    }

    private Map<String, String> snapshotDesconto(Pedido pedido) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("tipoDesconto", pedido.getTipoDesconto() == null ? "" : pedido.getTipoDesconto().name());
        valores.put("valorInformado", pedido.getDescontoInformado() == null ? ""
                : pedido.getDescontoInformado().toPlainString());
        valores.put("descontoPercentual", pedido.getDescontoPercentual() == null ? ""
                : pedido.getDescontoPercentual().toPlainString());
        valores.put("valorDesconto", pedido.getValorDesconto() == null ? "" : pedido.getValorDesconto().toPlainString());
        valores.put("valorTotal", pedido.getValorTotal() == null ? "" : pedido.getValorTotal().toPlainString());
        return valores;
    }

    public Pedido cancelar(String id, String justificativa, String usuarioGerente,
                           String senhaGerente, Authentication authentication) {
        exigirGerente(authentication);
        Usuario gerente = validarCredenciaisGerente(usuarioGerente, senhaGerente);
        Pedido pedido = buscarPorId(id);
        if (!podeCancelar(pedido)) {
            throw erro(HttpStatus.BAD_REQUEST, "Este pedido não pode mais ser cancelado.");
        }
        if (justificativa == null || justificativa.isBlank() || justificativa.trim().length() > 500) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe o motivo do cancelamento (até 500 caracteres).");
        }
        Usuario operador = usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> erro(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));
        String anterior = pedido.getStatus() == null ? "SEM_STATUS" : pedido.getStatus();
        pedido.setStatus("CANCELADO");
        pedido.setMotivoCancelamento(justificativa.trim());
        pedido.setCanceladoEm(Instant.now());
        pedido.setCanceladoPorUsername(authentication.getName());
        pedido.setCanceladoPorId(operador.getId());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "PEDIDO_CANCELADO", "PEDIDO", id,
                Map.of("status", anterior),
                Map.of("status", "CANCELADO", "operador", operador.getUsername(),
                        "gerenteAutorizador", gerente.getUsername()), justificativa.trim());
        return salvo;
    }

    private BigDecimal calcularDesconto(Pedido.TipoDesconto tipo, BigDecimal valor, BigDecimal base) {
        if (tipo == null) tipo = Pedido.TipoDesconto.SEM_DESCONTO;
        if (tipo == Pedido.TipoDesconto.SEM_DESCONTO) return ZERO;
        if (tipo != Pedido.TipoDesconto.PERCENTUAL) {
            throw erro(HttpStatus.BAD_REQUEST, "Somente desconto percentual é permitido.");
        }
        if (valor == null || valor.signum() < 0) throw erro(HttpStatus.BAD_REQUEST, "O desconto não pode ser negativo.");
        if (valor.compareTo(PERCENTUAL_MAXIMO) > 0) {
            throw erro(HttpStatus.BAD_REQUEST, "O desconto percentual não pode ultrapassar 20%.");
        }
        BigDecimal desconto = base.multiply(valor).divide(CEM, 2, RoundingMode.HALF_UP);
        if (desconto.compareTo(base) > 0) {
            throw erro(HttpStatus.BAD_REQUEST, "O desconto não pode deixar o total negativo.");
        }
        return desconto.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal obterSubtotal(Pedido pedido) {
        if (pedido.getSubtotalProdutos() != null) return arredondar(pedido.getSubtotalProdutos());
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "Não é possível recalcular os valores deste pedido antigo.");
        }
        return pedido.getItens().stream()
                .filter(item -> item != null && item.getPrecoUnitario() != null)
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private void atualizarTroco(Pedido pedido) {
        if (pedido.getFormaPagamento() != Pedido.FormaPagamento.DINHEIRO || pedido.getTrocoPara() == null) {
            pedido.setTrocoPara(null);
            pedido.setValorTroco(null);
            return;
        }
        BigDecimal trocoPara = arredondar(pedido.getTrocoPara());
        if (trocoPara.compareTo(pedido.getValorTotal()) < 0) {
            throw erro(HttpStatus.BAD_REQUEST, "O valor informado para troco deve ser igual ou maior que o total.");
        }
        pedido.setTrocoPara(trocoPara);
        pedido.setValorTroco(trocoPara.subtract(pedido.getValorTotal()).setScale(2, RoundingMode.HALF_UP));
    }

    private void exigirAntesDaSaida(Pedido pedido) {
        if (pedido.getStatus() == null || !List.of("RECEBIDO", "EM_PREPARACAO", "EM_PREPARO", "PRONTO", "PRONTO_PARA_RETIRADA")
                .contains(pedido.getStatus())) {
            throw erro(HttpStatus.BAD_REQUEST, "Esta operação só é permitida antes da saída ou retirada do pedido.");
        }
    }

    private void exigirGerente(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_GERENTE".equals(authority.getAuthority()))) {
            throw erro(HttpStatus.FORBIDDEN, "Somente GERENTE pode executar esta operação.");
        }
    }

    private Usuario validarCredenciaisGerente(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw erro(HttpStatus.FORBIDDEN,
                    "Não foi possível autorizar a operação. Verifique as credenciais do gerente.");
        }
        Usuario gerente = usuarioRepository.findByUsername(username.trim()).orElse(null);
        if (gerente == null || !"GERENTE".equals(gerente.getRole()) || gerente.getPassword() == null
                || !passwordEncoder.matches(password, gerente.getPassword())) {
            throw erro(HttpStatus.FORBIDDEN,
                    "Não foi possível autorizar a operação. Verifique as credenciais do gerente.");
        }
        return gerente;
    }

    private void exigirPerfilOperacional(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> List.of("ROLE_GERENTE", "ROLE_ATENDENTE")
                        .contains(authority.getAuthority()))) {
            throw erro(HttpStatus.FORBIDDEN,
                    "Somente GERENTE ou ATENDENTE pode registrar ou avançar pedidos.");
        }
    }

    private void validarAutenticacao(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw erro(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
    }

    private String enderecoCliente(Cliente cliente) {
        List<String> partes = new ArrayList<>();
        adicionarEndereco(partes, cliente.getLogradouro());
        adicionarEndereco(partes, cliente.getNumero());
        adicionarEndereco(partes, cliente.getComplemento());
        adicionarEndereco(partes, cliente.getBairro());
        adicionarEndereco(partes, cliente.getCidade());
        adicionarEndereco(partes, cliente.getEstado());
        adicionarEndereco(partes, cliente.getCep());
        String endereco = partes.isEmpty() ? limpar(cliente.getEndereco()) : String.join(", ", partes);
        return endereco;
    }

    private void adicionarEndereco(List<String> partes, String valor) {
        String limpo = limpar(valor);
        if (limpo != null) partes.add(limpo);
    }

    private String limpar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }

    private BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? ZERO : arredondar(valor);
    }

    private ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
