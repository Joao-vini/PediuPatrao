package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DashboardServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    DashboardService service = new DashboardService(mongo);
    DashboardService.Filtro filtro = service.filtro("2026-10-01", "2026-10-03", null, null);
    Pedido pedido(String status, String total) {
        Pedido p = new Pedido(); p.setStatus(status); p.setDataHora(Instant.parse("2026-10-02T02:59:59Z"));
        p.setValorTotal(total == null ? null : new BigDecimal(total)); return p;
    }
    @Test void calculaTotaisLiquidosSemCanceladosOuValoresAusentes() {
        Pedido p = pedido("RECEBIDO", "80.00"); p.setValorDesconto(new BigDecimal("20.00"));
        var resumo = service.calcular(filtro, List.of(p, pedido("FINALIZADO", "20"), pedido("CANCELADO", "900"), pedido(null, null)));
        assertEquals(new BigDecimal("100.00"), resumo.indicadores().faturamento());
        assertEquals(new BigDecimal("50.00"), resumo.indicadores().ticketMedio());
        assertEquals(new BigDecimal("20.00"), resumo.indicadores().descontos());
        assertEquals(4, resumo.indicadores().pedidos()); assertEquals(2, resumo.indicadores().pedidosValidos());
        assertEquals(1, resumo.indicadores().cancelados()); assertEquals(1, resumo.indicadores().semValor());
        assertEquals(new BigDecimal("100.00"), resumo.vendas().getFirst().faturamento());
        assertEquals(3, resumo.vendas().size());
        assertEquals(4, resumo.situacao().stream().mapToLong(DashboardService.Situacao::quantidade).sum());
    }
    @Test void rankingUsaQuantidadeEPrecoHistoricoComDesempatePorId() {
        Pedido p = pedido("FINALIZADO", "90");
        Pedido.ItemPedido a = new Pedido.ItemPedido(); a.setProdutoId("a"); a.setNomeProduto("Pizza"); a.setQuantidade(3); a.setPrecoUnitario(new BigDecimal("30"));
        p.setItens(List.of(a));
        Pedido cancelado = pedido("CANCELADO", "90"); cancelado.setItens(List.of(a));
        var ranking = service.calcular(filtro, List.of(p, cancelado)).produtos();
        assertEquals(1, ranking.size()); assertEquals(3, ranking.getFirst().quantidade());
        assertEquals(new BigDecimal("90.00"), ranking.getFirst().valorBruto());
    }
    @Test void periodoVazioTemZerosSemDivisaoPorZero() {
        var resumo = service.calcular(filtro, List.of());
        assertEquals(new BigDecimal("0.00"), resumo.indicadores().ticketMedio());
        assertTrue(resumo.produtos().isEmpty()); assertTrue(resumo.situacao().isEmpty());
    }
    @Test void rejeitaFiltrosInvalidos() {
        for (String[] dados : List.of(new String[]{"2026-02-30","2026-03-01",null,null},
                new String[]{"2026-10-03","2026-10-01",null,null}, new String[]{"2020-01-01","2026-01-01",null,null},
                new String[]{"2026-10-01","2026-10-03","QUALQUER",null}, new String[]{"2026-10-01","2026-10-03",null,"INVALIDO"})) {
            assertThrows(ResponseStatusException.class, () -> service.filtro(dados[0], dados[1], dados[2], dados[3]));
        }
        verifyNoInteractions(mongo);
    }
    @Test void consultaAplicaLimitesInclusivoExclusivoEFiltrosNoMongo() {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());
        service.consultar(service.filtro("2026-10-01", "2026-10-03", "RECEBIDO", "RETIRADA"));
        var captor = org.mockito.ArgumentCaptor.forClass(Query.class); verify(mongo).find(captor.capture(), eq(Pedido.class));
        var query = captor.getValue().getQueryObject();
        org.bson.Document datas = (org.bson.Document) query.get("dataHora");
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), datas.get("$gte"));
        assertEquals(Instant.parse("2026-10-04T03:00:00Z"), datas.get("$lt"));
        assertEquals("RECEBIDO", query.get("status")); assertEquals(Pedido.TipoPedido.RETIRADA, query.get("tipoPedido"));
        assertFalse(captor.getValue().getFieldsObject().containsKey("clienteNome"));
        assertEquals(50001, captor.getValue().getLimit());
    }
    @Test void recusaVolumeExcessivoSemRetornarTotaisParciais() {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(Collections.nCopies(50001, new Pedido()));
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.consultar(filtro)).getStatusCode().value());
    }
    @Test void distribuicaoPorTipoIncluiCanceladosEAusentesSemAlterarFinanceiro() {
        Pedido entrega = pedido("RECEBIDO", "80"); entrega.setTipoPedido(Pedido.TipoPedido.ENTREGA);
        Pedido cancelado = pedido("CANCELADO", "900"); cancelado.setTipoPedido(Pedido.TipoPedido.RETIRADA);
        var resumo = service.calcular(filtro, List.of(entrega, cancelado, pedido(null, null)));
        assertEquals(3, resumo.tipos().stream().mapToLong(DashboardService.DistribuicaoTipo::quantidade).sum());
        assertEquals(3, resumo.tipos().size());
        assertEquals(new BigDecimal("80.00"), resumo.indicadores().faturamento());
        assertTrue(resumo.tipos().stream().anyMatch(t -> "SEM_TIPO".equals(t.tipo())));
    }
}
