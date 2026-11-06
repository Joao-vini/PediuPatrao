package com.umc.pediupatrao.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MongoTopologyCheckTest {
    private MongoTemplate mongo=mock(MongoTemplate.class,RETURNS_DEEP_STUBS);
    @Test void aceitaReplicaSetEClusterCompativel() {
        when(mongo.getMongoDatabaseFactory().getMongoDatabase("admin").runCommand(any(Document.class)))
                .thenReturn(new Document("setName","rs0"),new Document("msg","isdbgrid"));
        var check=new MongoTopologyCheck(mongo);assertDoesNotThrow(()->check.run());assertDoesNotThrow(()->check.run());
    }
    @Test void recusaStandaloneSemExecutarEscritas() {
        when(mongo.getMongoDatabaseFactory().getMongoDatabase("admin").runCommand(any(Document.class))).thenReturn(new Document("ok",1));
        assertTrue(assertThrows(IllegalStateException.class,()->new MongoTopologyCheck(mongo).run()).getMessage().contains("standalone"));
        verify(mongo,never()).save(any());verify(mongo,never()).createCollection(anyString());
    }
    @Test void falhaDeConexaoNaoExpoeDetalhesInternos() {
        when(mongo.getMongoDatabaseFactory().getMongoDatabase("admin").runCommand(any(Document.class)))
                .thenThrow(new IllegalStateException("credencial-interna-nao-publicar"));
        var erro=assertThrows(IllegalStateException.class,()->new MongoTopologyCheck(mongo).run());
        assertFalse(erro.getMessage().contains("credencial-interna"));assertNull(erro.getCause());
    }
}
