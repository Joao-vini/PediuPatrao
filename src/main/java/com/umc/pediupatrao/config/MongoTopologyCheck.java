package com.umc.pediupatrao.config;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Verifica apenas metadados antes dos inicializadores que podem escrever. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoTopologyCheck implements CommandLineRunner {
    private final MongoTemplate mongo;
    public MongoTopologyCheck(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void run(String... args) {
        Document hello;
        try {
            hello = mongo.getMongoDatabaseFactory().getMongoDatabase("admin").runCommand(new Document("hello", 1));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Não foi possível verificar o MongoDB. Confira a conexão configurada; nenhum inicializador de dados foi executado.");
        }
        if (!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) {
            throw new IllegalStateException("As operações auditadas exigem MongoDB replica set ou cluster compatível. O servidor é standalone; configure a infraestrutura sem apagar os dados existentes. Nenhum inicializador de dados foi executado.");
        }
    }
}
