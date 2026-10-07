package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/usuarios/**", "/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/produtos", "/produtos/**", "/api/produtos", "/api/produtos/**")
                                .hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers(HttpMethod.GET, "/api/auditoria", "/api/auditoria/**", "/auditoria", "/auditoria/**")
                                .hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/api/auditoria", "/api/auditoria/**", "/auditoria", "/auditoria/**")
                                .denyAll()

                        // Descontos continuam exclusivos do GERENTE.
                        .requestMatchers("/api/pedidos/*/desconto", "/api/pedidos/*/cancelar", "/pedidos/*/desconto")
                                .hasRole("GERENTE")
                        // RF04: a sessão também deve pertencer a GERENTE, mesmo com credenciais de autorização.
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/cancelamento", "/api/pedidos/*/cancelamento")
                                .hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/pedidos/autorizar-desconto")
                                .hasRole("GERENTE")
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/status")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/pedidos/novo").hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**", "/pedidos", "/pedidos/**")
                                .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/**")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/api/pedidos/**", "/pedidos/**").hasRole("GERENTE")

                        .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/**")
                                .hasRole("ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/**", "/clientes", "/clientes/**")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/clientes", "/api/clientes/**", "/clientes/**")
                                .hasRole("ATENDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasRole("ATENDENTE")
                        .requestMatchers("/api/clientes/**", "/clientes/**").denyAll()

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
