package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder,
            @Value("${app.bootstrap.admin.username}") String username,
            @Value("${app.bootstrap.admin.password}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Informe as credenciais externas para inicializar o administrador.");
        }
        return args -> {

            var adminSalvo = repo.findByUsername(username.trim());
            if (adminSalvo.isEmpty()) {


                Usuario user = new Usuario();
                user.setUsername(username.trim());
                user.setPassword(encoder.encode(password));
                user.setRole("ADMIN");
                user.setAtivo(true);

                repo.save(user);

                System.out.println("Usuário admin criado!");
            }
        };
    }
}
