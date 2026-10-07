package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Endpoint para listar todos os usuários
    @GetMapping
    public ResponseEntity<Iterable<Usuario>> listarTodos() {
        Iterable<Usuario> usuarios = usuarioService.listarTodos();
        usuarios.forEach(usuario -> usuario.setPassword(null));
        return ResponseEntity.ok(usuarios);
    }

    // Endpoint para buscar um usuário por ID
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable String id) {
        Optional<Usuario> usuario = usuarioService.buscarPorId(id);
        usuario.ifPresent(u -> u.setPassword(null));
        return usuario.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // Endpoint para criar um novo usuário
    @PostMapping
    public ResponseEntity<Usuario> criarUsuario(@RequestBody Usuario usuario, Authentication authentication) {
        usuarioService.salvarUsuario(usuario, authentication);
        usuario.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    // Endpoint para atualizar um usuário
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> atualizarUsuario(@PathVariable String id, @RequestBody Usuario usuarioAtualizado,
                                                    Authentication authentication) {
        if (usuarioService.buscarPorId(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        usuarioService.atualizarUsuario(id, usuarioAtualizado, authentication);
        Usuario atualizado = usuarioService.buscarPorId(id).orElseThrow();
        atualizado.setPassword(null);
        return ResponseEntity.ok(atualizado);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Usuario> atualizarStatus(@PathVariable String id, @RequestParam boolean ativo,
                                                   Authentication authentication) {
        Usuario usuario = usuarioService.atualizarStatus(id, ativo, authentication);
        usuario.setPassword(null);
        return ResponseEntity.ok(usuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirUsuario(@PathVariable String id, Authentication authentication) {
        usuarioService.excluirUsuario(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
