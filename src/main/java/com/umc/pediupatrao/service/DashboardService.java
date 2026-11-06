package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final MongoTemplate mongo;
    public DashboardService(MongoTemplate mongo) { this.mongo = mongo; }

    public record Filtro(LocalDate inicio, LocalDate fim, String status, Pedido.TipoPedido tipo) { }
    public record Indicadores(BigDecimal faturamento, long pedidos, long pedidosValidos,
            long cancelados, BigDecimal ticketMedio, BigDecimal descontos, long semValor) { }
    public record VendaDia(LocalDate data, BigDecimal faturamento, long pedidos) { }
    public record Situacao(String status, long quantidade) { }
    public record DistribuicaoTipo(String tipo, long quantidade) { }
    public record ProdutoVendido(String produtoId, String nome, long quantidade, BigDecimal valorBruto) { }
    public record Resumo(Filtro filtro, Indicadores indicadores, List<VendaDia> vendas,
            List<Situacao> situacao, List<ProdutoVendido> produtos, List<DistribuicaoTipo> tipos) { }

    public Filtro filtro(String inicio, String fim, String status, String tipo) {
        try {
            LocalDate hoje = LocalDate.now(FUSO);
            LocalDate de = inicio == null || inicio.isBlank() ? hoje.withDayOfMonth(1) : LocalDate.parse(inicio);
            LocalDate ate = fim == null || fim.isBlank() ? hoje : LocalDate.parse(fim);
            if (de.getYear() < 1 || ate.getYear() > 9998 || ate.isBefore(de)
                    || java.time.temporal.ChronoUnit.DAYS.between(de, ate) > 365) throw new IllegalArgumentException();
            String estado = status == null || status.isBlank() ? null : status;
            if (estado != null && !Set.of("RECEBIDO", "EM_PREPARO", "EM_PREPARACAO", "PRONTO",
                    "PRONTO_PARA_RETIRADA", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO", "SEM_STATUS").contains(estado)) {
                throw new IllegalArgumentException();
            }
            return new Filtro(de, ate, estado, tipo == null || tipo.isBlank() ? null : Pedido.TipoPedido.valueOf(tipo));
        } catch (java.time.DateTimeException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Filtros inválidos: use datas ISO, início até fim (máximo 366 dias), status e tipo conhecidos.");
        }
    }

    public Resumo consultar(Filtro filtro) {
        Query query = Query.query(Criteria.where("dataHora").gte(filtro.inicio().atStartOfDay(FUSO).toInstant())
                .lt(filtro.fim().plusDays(1).atStartOfDay(FUSO).toInstant()));
        if (filtro.status() != null) query.addCriteria(Criteria.where("status")
                .is("SEM_STATUS".equals(filtro.status()) ? null : filtro.status()));
        if (filtro.tipo() != null) query.addCriteria(Criteria.where("tipoPedido").is(filtro.tipo()));
        // Projeção limita memória e evita carregar dados de clientes, senhas ou observações.
        query.fields().include("dataHora").include("status").include("tipoPedido").include("valorTotal").include("valorDesconto")
                .include("itens.produtoId").include("itens.nomeProduto").include("itens.quantidade")
                .include("itens.precoUnitario");
        query.limit(50001);
        List<Pedido> pedidos = mongo.find(query, Pedido.class);
        if (pedidos.size() > 50000) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "O período excede 50.000 pedidos. Reduza o intervalo ou aplique filtros.");
        return calcular(filtro, pedidos);
    }

    Resumo calcular(Filtro filtro, List<Pedido> pedidos) {
        BigDecimal total = BigDecimal.ZERO, descontos = BigDecimal.ZERO;
        long cancelados = 0, validos = 0, semValor = 0;
        Map<LocalDate, BigDecimal> valores = new TreeMap<>();
        Map<LocalDate, Long> contagens = new TreeMap<>();
        for (LocalDate d = filtro.inicio(); !d.isAfter(filtro.fim()); d = d.plusDays(1)) {
            valores.put(d, BigDecimal.ZERO); contagens.put(d, 0L);
        }
        Map<String, Long> estados = new TreeMap<>();
        Map<String, Long> tipos = new TreeMap<>();
        Map<String, ProdutoVendido> produtos = new HashMap<>();
        for (Pedido p : pedidos) {
            String estado = p.getStatus() == null ? "SEM_STATUS" : p.getStatus();
            estados.merge(estado, 1L, Long::sum);
            tipos.merge(p.getTipoPedido() == null ? "SEM_TIPO" : p.getTipoPedido().name(), 1L, Long::sum);
            if ("CANCELADO".equals(estado)) { cancelados++; continue; }
            if (p.getValorTotal() == null || p.getValorTotal().signum() < 0) { semValor++; continue; }
            validos++;
            total = total.add(p.getValorTotal());
            if (p.getValorDesconto() != null && p.getValorDesconto().signum() >= 0) descontos = descontos.add(p.getValorDesconto());
            LocalDate dia = p.getDataHora().atZone(FUSO).toLocalDate();
            valores.merge(dia, p.getValorTotal(), BigDecimal::add);
            contagens.merge(dia, 1L, Long::sum);
            if (p.getItens() != null) for (Pedido.ItemPedido item : p.getItens()) {
                if (item == null || item.getProdutoId() == null || item.getQuantidade() <= 0) continue;
                BigDecimal bruto = item.getPrecoUnitario() == null || item.getPrecoUnitario().signum() < 0
                        ? BigDecimal.ZERO : item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));
                ProdutoVendido anterior = produtos.get(item.getProdutoId());
                String nome = item.getNomeProduto() == null ? "Produto sem nome" : item.getNomeProduto();
                if (anterior != null && anterior.nome().compareTo(nome) < 0) nome = anterior.nome();
                produtos.put(item.getProdutoId(), new ProdutoVendido(item.getProdutoId(), nome,
                        item.getQuantidade() + (anterior == null ? 0 : anterior.quantidade()),
                        bruto.add(anterior == null ? BigDecimal.ZERO : anterior.valorBruto())));
            }
        }
        BigDecimal ticket = validos == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        Indicadores indicadores = new Indicadores(moeda(total), pedidos.size(), validos, cancelados,
                moeda(ticket), moeda(descontos), semValor);
        return new Resumo(filtro, indicadores,
                valores.entrySet().stream().map(e -> new VendaDia(e.getKey(), moeda(e.getValue()), contagens.get(e.getKey()))).toList(),
                estados.entrySet().stream().map(e -> new Situacao(e.getKey(), e.getValue())).toList(),
                produtos.values().stream().sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                        .thenComparing(ProdutoVendido::produtoId)).limit(10)
                        .map(p -> new ProdutoVendido(p.produtoId(), p.nome(), p.quantidade(), moeda(p.valorBruto()))).toList(),
                tipos.entrySet().stream().map(e -> new DistribuicaoTipo(e.getKey(), e.getValue())).toList());
    }
    private BigDecimal moeda(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
}
