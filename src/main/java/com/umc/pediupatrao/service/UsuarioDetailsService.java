package com.umc.pediupatrao.service;

import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final Set<String> PERFIS_VALIDOS = Set.of("ADMIN", "GERENTE", "ATENDENTE");

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        if (usuario.getRole() == null || !PERFIS_VALIDOS.contains(usuario.getRole())) {
            throw new UsernameNotFoundException("Perfil do usuário inválido");
        }

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(usuario.getRole())
                .disabled(!usuario.isAtivo())
                .build();
    }
}
