package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaConsultaTest {
    @Mock AuditoriaRepository repository;
    @Mock MongoTemplate mongo;
    @InjectMocks AuditoriaService service;

    @Test void filtraNoMongoComLimitesDeDataEPaginaVinteRegistros() {
        AtomicReference<Query> contagem = new AtomicReference<>();
        when(mongo.count(any(Query.class), eq(Auditoria.class))).thenAnswer(invocacao -> {
            contagem.set(Query.of(invocacao.getArgument(0)));
            return 45L;
        });
        when(mongo.find(any(Query.class), eq(Auditoria.class))).thenReturn(List.of(new Auditoria()));
        var resultado = service.consultar(" ana.* ", "PEDIDO_CRIADO", "PEDIDO", "2026-10-06", "2026-10-06", 1);
        ArgumentCaptor<Query> captura = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captura.capture(), eq(Auditoria.class));
        Query query = captura.getValue();
        assertEquals(20, query.getLimit());
        assertEquals(20, query.getSkip());
        assertEquals(new Document("dataHora", -1).append("id", -1), query.getSortObject());
        Document criterios = query.getQueryObject();
        assertEquals("PEDIDO_CRIADO", criterios.get("operacao"));
        assertEquals("PEDIDO", criterios.get("entidade"));
        assertEquals("\\Qana.*\\E", criterios.get("usuario", java.util.regex.Pattern.class).pattern());
        Document periodo = criterios.get("dataHora", Document.class);
        assertEquals(Instant.parse("2026-10-06T03:00:00Z"), periodo.get("$gte"));
        assertEquals(Instant.parse("2026-10-07T03:00:00Z"), periodo.get("$lt"));
        assertEquals(0, contagem.get().getLimit());
        assertEquals(0, contagem.get().getSkip());
        assertEquals(45, resultado.getTotalElements());
        assertEquals(3, resultado.getTotalPages());
        assertTrue(resultado.hasPrevious());
        assertTrue(resultado.hasNext());
        verifyNoInteractions(repository);
    }

    @Test void semFiltrosOrdenaEPaginaNoBanco() {
        when(mongo.count(any(Query.class), eq(Auditoria.class))).thenReturn(21L);
        when(mongo.find(any(Query.class), eq(Auditoria.class))).thenReturn(List.of());
        service.consultar("", "", "", "", "", 0);
        ArgumentCaptor<Query> captura = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captura.capture(), eq(Auditoria.class));
        assertTrue(captura.getValue().getQueryObject().isEmpty());
        assertEquals(20, captura.getValue().getLimit());
        assertEquals(-1, captura.getValue().getSortObject().get("dataHora"));
        verifyNoInteractions(repository);
    }

    @Test void validaDatasEPaginaAntesDeConsultarBanco() {
        assertThrows(ResponseStatusException.class, () -> service.consultar("", "", "", "2026-10-07", "2026-10-06", 0));
        assertThrows(ResponseStatusException.class, () -> service.consultar("", "", "", "2026-02-30", "", 0));
        assertThrows(ResponseStatusException.class, () -> service.consultar("", "", "", "", "", -1));
        verifyNoInteractions(mongo, repository);
    }

    @Test void resultadoVazioNaoCarregaDocumentosEPaginaExcessivaVaiParaUltima() {
        when(mongo.count(any(Query.class), eq(Auditoria.class))).thenReturn(0L);
        assertTrue(service.consultar("", "", "", "", "", 99).isEmpty());
        verify(mongo, never()).find(any(Query.class), eq(Auditoria.class));
        when(mongo.count(any(Query.class), eq(Auditoria.class))).thenReturn(21L);
        when(mongo.find(any(Query.class), eq(Auditoria.class))).thenReturn(List.of(new Auditoria()));
        assertEquals(1, service.consultar("", "", "", "", "", 99).getNumber());
    }

    @Test void formataDataEMantemEventoOriginalProtegendoDadosSensiveis() {
        Auditoria original = new Auditoria();
        original.setDataHora(Instant.parse("2026-10-06T17:16:32.677Z"));
        original.setOperacao("PEDIDO_CANCELADO");
        original.setEntidade("PEDIDO");
        original.setUsuario("atendente");
        original.setCamposAlterados(List.of("status", "senhaGerente", "Password_Hash", "access_token"));
        original.setValoresAnteriores(Map.of("status", "EM_PREPARO", "senhaGerente", "segredo-antigo"));
        original.setValoresNovos(Map.of("status", "CANCELADO", "operador", "atendente",
                "gerenteAutorizador", "gerente", "Password_Hash", "hash-secreto", "access_token", "token-secreto",
                "campoLegado", "password: segredo-legado"));
        original.setJustificativa("authorization=Bearer segredo");
        var exibicao = service.paraConsulta(original);
        assertEquals("06/10/2026 14:16:32", exibicao.dataHoraFormatada());
        assertEquals("Pedido cancelado", exibicao.operacaoDescricao());
        assertEquals("Pedido", exibicao.entidadeDescricao());
        assertEquals("atendente", exibicao.operador());
        assertEquals("gerente", exibicao.gerenteAutorizador());
        assertEquals(List.of("status"), exibicao.camposAlterados());
        assertFalse(exibicao.valoresAnteriores().containsKey("senhaGerente"));
        assertFalse(exibicao.valoresNovos().containsKey("Password_Hash"));
        assertFalse(exibicao.valoresNovos().containsKey("access_token"));
        assertEquals("[Dado sensível ocultado]", exibicao.valoresNovos().get("campoLegado"));
        assertEquals("[Dado sensível ocultado]", exibicao.justificativa());
        assertEquals("segredo-antigo", original.getValoresAnteriores().get("senhaGerente"));
        assertEquals(Instant.parse("2026-10-06T17:16:32.677Z"), original.getDataHora());
        verifyNoInteractions(repository, mongo);
    }

    @Test void mascaraHashETokenMesmoSobCampoGenerico() {
        Auditoria original = new Auditoria();
        original.setValoresNovos(Map.of("dado1", "$2a$10$" + "a".repeat(53),
                "dado2", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmEifQ.assinatura",
                "dado3", "Bearer token", "dado4", "a".repeat(64), "status", "PRONTO"));
        var exibicao = service.paraConsulta(original);
        for (String campo : List.of("dado1", "dado2", "dado3", "dado4")) {
            assertEquals("[Dado sensível ocultado]", exibicao.valoresNovos().get(campo));
        }
        assertEquals("PRONTO", exibicao.valoresNovos().get("status"));
    }
}
