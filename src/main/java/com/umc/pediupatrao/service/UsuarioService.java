package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UsuarioService {

    private static final Set<String> PERFIS_VALIDOS = Set.of("ADMIN", "GERENTE", "ATENDENTE");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Salva um novo usuário (criptografando a senha)
    public void salvarUsuario(Usuario usuario, Authentication authentication) {
        exigirAdmin(authentication);
        if (usuario.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O cadastro de um novo usuário não pode informar um ID existente.");
        }
        validarDadosUsuario(usuario, true);
        usuario.setUsername(usuario.getUsername().trim());
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword())); // Criptografa a senha
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }

    // Lista todos os usuários
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    // Atualiza o usuário usuários
    public void atualizarUsuario(String id, Usuario usuarioAtualizado, Authentication authentication) {
        exigirAdmin(authentication);
        validarDadosUsuario(usuarioAtualizado, false);
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (authentication != null && authentication.getName().equals(usuarioExistente.getUsername())
                && !authentication.getName().equals(usuarioAtualizado.getUsername().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não é possível alterar o próprio username enquanto a sessão estiver ativa.");
        }

        if (usuarioExistente.isAtivo() && "ADMIN".equals(usuarioExistente.getRole())
                && !"ADMIN".equals(usuarioAtualizado.getRole()) && contarAdministradoresAtivos() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O último ADMIN ativo não pode perder o perfil ADMIN.");
        }

        usuarioExistente.setUsername(usuarioAtualizado.getUsername().trim());
        usuarioExistente.setRole(usuarioAtualizado.getRole());

        // Só atualiza a senha se uma nova foi informada.
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        usuarioRepository.save(usuarioExistente);
    }

    public Usuario atualizarStatus(String id, boolean ativo, Authentication authentication) {
        exigirAdmin(authentication);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (usuario.isAtivo() == ativo) return usuario;

        if (!ativo) {
            if (authentication.getName().equals(usuario.getUsername())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O administrador não pode desativar a própria conta.");
            }
            if ("ADMIN".equals(usuario.getRole()) && contarAdministradoresAtivos() <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O sistema precisa manter pelo menos um ADMIN ativo.");
            }
        }

        usuario.setAtivo(ativo);
        Usuario salvo = usuarioRepository.save(usuario);
        String status = ativo ? "ATIVO" : "INATIVO";
        auditoriaService.registrar(authentication, ativo ? "USUARIO_ATIVADO" : "USUARIO_DESATIVADO",
                "USUARIO", usuario.getId(), Map.of("status", ativo ? "INATIVO" : "ATIVO"),
                Map.of("status", status, "usuarioAfetado", usuario.getUsername(),
                        "administradorResponsavel", authentication.getName()), null);
        return salvo;
    }

    public void excluirUsuario(String id, Authentication authentication) {
        exigirAdmin(authentication);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (authentication.getName().equals(usuario.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador não pode excluir a própria conta.");
        }
        if ("ADMIN".equals(usuario.getRole()) && usuario.isAtivo() && contarAdministradoresAtivos() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O sistema precisa manter pelo menos um ADMIN ativo.");
        }

        // Auditoria é salva antes da remoção e contém apenas identificadores não secretos.
        auditoriaService.registrar(authentication, "USUARIO_EXCLUIDO", "USUARIO", usuario.getId(), Map.of(),
                Map.of("administradorResponsavel", authentication.getName(),
                        "usuarioExcluido", usuario.getUsername(), "perfilExcluido", usuario.getRole()), null);
        usuarioRepository.delete(usuario);
    }

    private long contarAdministradoresAtivos() {
        long total = 0;
        for (Usuario usuario : usuarioRepository.findAll()) {
            if ("ADMIN".equals(usuario.getRole()) && usuario.isAtivo()) total++;
        }
        return total;
    }

    private void exigirAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente ADMIN pode alterar usuários.");
        }
    }

    private void validarDadosUsuario(Usuario usuario, boolean senhaObrigatoria) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O nome de usuário é obrigatório.");
        }
        if (usuario.getRole() == null || !PERFIS_VALIDOS.contains(usuario.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE.");
        }
        if (senhaObrigatoria && (usuario.getPassword() == null || usuario.getPassword().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha é obrigatória para novos usuários.");
        }
    }
}
