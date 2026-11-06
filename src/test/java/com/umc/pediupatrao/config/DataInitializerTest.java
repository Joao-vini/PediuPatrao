package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DataInitializerTest {
    @Test void bootstrapDesabilitadoPorPadrao() {
        new ApplicationContextRunner().withUserConfiguration(DataInitializer.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("initDatabase"));
        });
    }

    @Test void criaSomenteComCredenciaisExternasESenhaCodificada() throws Exception {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        String segredo = UUID.randomUUID().toString();
        when(repo.findByUsername("operadorInicial")).thenReturn(Optional.empty());
        when(encoder.encode(segredo)).thenReturn("resultado-codificado");
        new DataInitializer().initDatabase(repo, encoder, "operadorInicial", segredo).run();
        verify(repo).save(argThat(u -> "ADMIN".equals(u.getRole()) && u.isAtivo()
                && "resultado-codificado".equals(u.getPassword())));
    }

    @Test void naoPromoveNemReativaContaExistente() throws Exception {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Usuario usuario = new Usuario();
        usuario.setRole("ATENDENTE");
        usuario.setAtivo(false);
        when(repo.findByUsername("existente")).thenReturn(Optional.of(usuario));
        new DataInitializer().initDatabase(repo, encoder, "existente", UUID.randomUUID().toString()).run();
        assertEquals("ATENDENTE", usuario.getRole());
        assertFalse(usuario.isAtivo());
        verify(repo, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test void rejeitaConfiguracaoVazia() {
        assertThrows(IllegalArgumentException.class, () -> new DataInitializer().initDatabase(
                mock(UsuarioRepository.class), mock(PasswordEncoder.class), " ", ""));
    }
}
