package com.umc.pediupatrao.service;

import com.mongodb.ConnectionString;
import com.umc.pediupatrao.PizzariaPedidosApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PP_TEST_MONGO_URI",matches=".+")
class NormalStartupIntegrationTest {
    @Test void inicializacaoNormalNaoCriaDadosSinteticosNemAdministrador() throws Exception {
        var original=new ConnectionString(System.getenv("PP_TEST_MONGO_URI"));
        assertEquals(List.of("127.0.0.1:27028"),original.getHosts());
        String banco="pediupatrao_test_normal_"+UUID.randomUUID().toString().replace("-","");
        String uri="mongodb://127.0.0.1:27028/"+banco+"?replicaSet=codexDashboard&serverSelectionTimeoutMS=5000";
        try(var context=new SpringApplicationBuilder(PizzariaPedidosApplication.class).run(
                "--spring.data.mongodb.uri="+uri,"--app.bootstrap.admin.enabled=false","--server.address=127.0.0.1","--server.port=0",
                "--spring.autoconfigure.exclude=","--logging.level.root=WARN")) {
            var mongo=context.getBean(MongoTemplate.class);
            for(String collection:List.of("pedidos","produtos","clientes","usuarios","auditorias")) {
                assertEquals(0,mongo.getCollection(collection).countDocuments(),collection);
            }
            int porta=((ServletWebServerApplicationContext)context).getWebServer().getPort();
            var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            var login=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+porta+"/login")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,login.statusCode());assertFalse(login.body().contains("AMBIENTE DE DEMONSTRAÇÃO"));
            var dashboard=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+porta+"/dashboard")).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertEquals(302,dashboard.statusCode());
        }
    }
}
