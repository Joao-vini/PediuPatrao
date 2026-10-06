package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

@Service
public class PedidoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository,
                         ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository,
                         UsuarioRepository usuarioRepository,
                         AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    public Pedido criarPedido(Pedido pedido, Authentication authentication) {
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Os dados do pedido são obrigatórios.");
        }
        if (pedido.getClienteId() == null || !clienteRepository.existsById(pedido.getClienteId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente não encontrado.");
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }

        Usuario responsavel = usuarioRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuário autenticado não encontrado."));

        BigDecimal descontoPercentual = pedido.getDescontoPercentual() == null
                ? ZERO : pedido.getDescontoPercentual();
        if (descontoPercentual.signum() < 0 || descontoPercentual.compareTo(new BigDecimal("20")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O desconto máximo permitido é 20 por cento.");
        }
        if (descontoPercentual.signum() > 0 && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_GERENTE".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente GERENTE pode aplicar desconto.");
        }
        descontoPercentual = descontoPercentual.setScale(2, RoundingMode.HALF_UP);
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O pedido deve possuir ao menos um item.");
        }

        List<Pedido.ItemPedido> itensCalculados = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        for (Pedido.ItemPedido itemRecebido : pedido.getItens()) {
            if (itemRecebido == null || itemRecebido.getProdutoId() == null
                    || itemRecebido.getQuantidade() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada item deve informar um produto e uma quantidade positiva.");
            }

            Produto produto = produtoRepository.findById(itemRecebido.getProdutoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Produto não encontrado: " + itemRecebido.getProdutoId()));
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O produto não possui um preço válido: " + produto.getId());
            }

            BigDecimal precoUnitario = BigDecimal.valueOf(produto.getPreco())
                    .setScale(2, RoundingMode.HALF_UP);
            Pedido.ItemPedido itemCalculado = new Pedido.ItemPedido();
            itemCalculado.setProdutoId(produto.getId());
            itemCalculado.setNomeProduto(produto.getNome());
            itemCalculado.setQuantidade(itemRecebido.getQuantidade());
            itemCalculado.setPrecoUnitario(precoUnitario);
            itensCalculados.add(itemCalculado);

            subtotal = subtotal.add(precoUnitario.multiply(BigDecimal.valueOf(itemCalculado.getQuantidade())));
        }

        BigDecimal valorDesconto = subtotal.multiply(descontoPercentual)
                .divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setId(null);
        pedido.setTipoPizza(null);
        pedido.setSabor(null);
        pedido.setQuantidade(0);
        pedido.setItens(itensCalculados);
        pedido.setDescontoPercentual(descontoPercentual.setScale(2, RoundingMode.HALF_UP));
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP));
        pedido.setUsuarioResponsavelId(responsavel.getId());
        pedido.setUsuarioResponsavelUsername(responsavel.getUsername());
        pedido.setDataHora(Instant.now());
        pedido.setStatus("RECEBIDO");

        Pedido salvo = pedidoRepository.save(pedido);
        String resumoItens = salvo.getItens().stream()
                .map(item -> item.getNomeProduto() + " x" + item.getQuantidade()
                        + " @ " + item.getPrecoUnitario().toPlainString())
                .reduce((a, b) -> a + "; " + b).orElse("");
        auditoriaService.registrar(authentication, "CRIACAO", "PEDIDO", salvo.getId(), Map.of(),
                Map.of("status", salvo.getStatus(), "valorTotal", salvo.getValorTotal().toPlainString(),
                        "itens", resumoItens), null);
        if (valorDesconto.signum() > 0) {
            auditoriaService.registrar(authentication, "DESCONTO", "PEDIDO", salvo.getId(),
                    Map.of("descontoPercentual", ZERO.toPlainString()),
                    Map.of("descontoPercentual", salvo.getDescontoPercentual().toPlainString(),
                            "valorDesconto", salvo.getValorDesconto().toPlainString()), null);
        }
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido atualizarStatus(String id, String status, Authentication authentication) {
        exigirGerente(authentication);
        Pedido pedido = buscarPedido(id);
        String anterior = pedido.getStatus();
        if (anterior == null || !transicaoPermitida(anterior, status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transição de status inválida.");
        }
        pedido.setStatus(status);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "MUDANCA_STATUS", "PEDIDO", id,
                Map.of("status", anterior), Map.of("status", status), null);
        return salvo;
    }

    public Pedido aplicarDesconto(String id, BigDecimal percentual, Authentication authentication) {
        exigirGerente(authentication);
        Pedido pedido = buscarPedido(id);
        exigirAntesDaSaida(pedido);
        if (percentual == null || percentual.signum() < 0 || percentual.compareTo(new BigDecimal("20")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O desconto deve estar entre 0 e 20 por cento.");
        }
        percentual = percentual.setScale(2, RoundingMode.HALF_UP);
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível recalcular o desconto neste pedido legado.");
        }
        BigDecimal subtotal = pedido.getItens().stream()
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal descontoAnterior = pedido.getDescontoPercentual() == null ? ZERO : pedido.getDescontoPercentual();
        BigDecimal totalAnterior = pedido.getValorTotal() == null ? subtotal : pedido.getValorTotal();
        BigDecimal valorDesconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP));
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "DESCONTO", "PEDIDO", id,
                Map.of("descontoPercentual", descontoAnterior.toPlainString(),
                        "valorTotal", totalAnterior.toPlainString()),
                Map.of("descontoPercentual", salvo.getDescontoPercentual().toPlainString(),
                        "valorDesconto", salvo.getValorDesconto().toPlainString(),
                        "valorTotal", salvo.getValorTotal().toPlainString()), null);
        return salvo;
    }

    public Pedido cancelar(String id, String justificativa, Authentication authentication) {
        exigirGerente(authentication);
        Pedido pedido = buscarPedido(id);
        exigirAntesDaSaida(pedido);
        if (justificativa == null || justificativa.isBlank() || justificativa.trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe uma justificativa de cancelamento com até 500 caracteres.");
        }
        String anterior = pedido.getStatus();
        pedido.setStatus("CANCELADO");
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(authentication, "CANCELAMENTO", "PEDIDO", id,
                Map.of("status", anterior == null ? "SEM_STATUS" : anterior),
                Map.of("status", "CANCELADO"), justificativa.trim());
        return salvo;
    }

    private Pedido buscarPedido(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    }

    private void exigirGerente(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_GERENTE".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente GERENTE pode executar esta operação.");
        }
    }

    private void exigirAntesDaSaida(Pedido pedido) {
        String status = pedido.getStatus();
        if (!List.of("RECEBIDO", "EM_PREPARACAO", "EM_PREPARO", "PRONTO").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A operação só é permitida antes da saída para entrega ou retirada.");
        }
    }

    private boolean transicaoPermitida(String atual, String novo) {
        if ("CANCELADO".equals(atual)) return false;
        return switch (atual) {
            case "RECEBIDO" -> "EM_PREPARACAO".equals(novo);
            case "EM_PREPARACAO", "EM_PREPARO" -> "PRONTO".equals(novo);
            case "PRONTO" -> "SAIU_PARA_ENTREGA".equals(novo) || "RETIRADO".equals(novo);
            case "SAIU_PARA_ENTREGA", "RETIRADO" -> "FINALIZADO".equals(novo);
            default -> false;
        };
    }

}
