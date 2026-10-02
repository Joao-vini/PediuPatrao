package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo) {
        return args -> {

            var adminSalvo = repo.findByUsername("admin");
            if (adminSalvo.isEmpty()) {

                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN");

                repo.save(user);

                System.out.println("Usuário admin criado!");
            } else {
                Usuario admin = adminSalvo.get();
                if (!"ADMIN".equals(admin.getRole())) {
                    admin.setRole("ADMIN");
                    repo.save(admin);
                }
            }
        };
    }
}
