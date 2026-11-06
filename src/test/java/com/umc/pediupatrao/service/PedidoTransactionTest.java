package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class PedidoTransactionTest {
    @Test void falhasDePedidoOuAuditoriaSolicitamRollbackSemCommit() {
        for (boolean falhaAuditoria : List.of(false, true)) {
            PedidoRepository pedidos = mock(PedidoRepository.class);
            AuditoriaService auditoria = mock(AuditoriaService.class);
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            TransactionStatus transaction = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(transaction);
            UsuarioRepository usuarios = mock(UsuarioRepository.class);
            var gerente = new com.umc.pediupatrao.entity.Usuario(); gerente.setRole("GERENTE");
            when(usuarios.findByUsername("gestor")).thenReturn(Optional.of(gerente));
            PedidoService target = new PedidoService(pedidos, mock(ClienteRepository.class), mock(ProdutoRepository.class),
                    usuarios, auditoria, mock(PasswordEncoder.class));
            ProxyFactory factory = new ProxyFactory(target);
            factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            PedidoService service = (PedidoService) factory.getProxy();
            Pedido p = new Pedido(); p.setStatus("RECEBIDO"); p.setSubtotalProdutos(new BigDecimal("100"));
            when(pedidos.findById("p1")).thenReturn(Optional.of(p));
            if (falhaAuditoria) {
                when(pedidos.save(any())).thenReturn(p);
                doThrow(new IllegalStateException("falha simulada")).when(auditoria).registrar(any(), any(), any(), any(), anyMap(), anyMap(), any());
            } else when(pedidos.save(any())).thenThrow(new IllegalStateException("falha simulada"));
            var auth = UsernamePasswordAuthenticationToken.authenticated("gestor", null, List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
            assertThrows(IllegalStateException.class, () -> service.aplicarDesconto("p1", BigDecimal.TEN, auth));
            verify(manager).rollback(transaction); verify(manager, never()).commit(any());
            if (!falhaAuditoria) verifyNoInteractions(auditoria);
        }
    }
    @Test void todasAsEntradasDeEscritaAuditadaTemTransacao() {
        var source = new AnnotationTransactionAttributeSource();
        for (var method : PedidoService.class.getMethods()) {
            if (Set.of("criarPedido", "aplicarDesconto", "removerDesconto", "cancelar", "excluir", "atualizarStatus").contains(method.getName())) {
                assertNotNull(source.getTransactionAttribute(method, PedidoService.class), method.toString());
            }
        }
    }
}
