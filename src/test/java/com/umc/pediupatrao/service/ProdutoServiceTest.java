package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {
    @Mock ProdutoRepository produtos;
    @Mock PedidoRepository pedidos;
    @InjectMocks ProdutoService service;

    private Produto produto() {
        Produto produto = new Produto();
        produto.setNome("Pizza");
        produto.setTipo("Pizza");
        produto.setPreco(30.50);
        produto.setAtivo(true);
        return produto;
    }

    @Test void edicaoMantemIdEDadosHistoricos() {
        Produto existente = produto();
        existente.setId("p1");
        Produto dados = produto();
        dados.setId("p1");
        dados.setNome("Novo nome");
        dados.setPreco(40.0);
        when(produtos.findById("p1")).thenReturn(Optional.of(existente));
        when(produtos.save(existente)).thenReturn(existente);
        Produto salvo = service.salvar(dados);
        assertEquals("p1", salvo.getId());
        assertEquals("Novo nome", salvo.getNome());
        assertEquals(40.0, salvo.getPreco());
        verifyNoInteractions(pedidos);
    }

    @Test void idInexistenteNaoCriaOutroProduto() {
        Produto dados = produto();
        dados.setId("ausente");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.salvar(dados)).getStatusCode());
        verify(produtos, never()).save(any());
    }

    @Test void cadastroNaoPodeSobrescreverProduto() {
        Produto dados = produto();
        dados.setId("p1");
        assertThrows(ResponseStatusException.class, () -> service.novoProduto(dados));
        verifyNoInteractions(produtos, pedidos);
    }

    @Test void rejeitaDadosInvalidosSemPersistir() {
        for (Double preco : new Double[]{null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.001}) {
            Produto dados = produto();
            dados.setPreco(preco);
            assertThrows(ResponseStatusException.class, () -> service.salvar(dados));
        }
        Produto dados = produto();
        dados.setNome(" ");
        assertThrows(ResponseStatusException.class, () -> service.salvar(dados));
        dados.setNome("Pizza");
        dados.setTipo("invalido");
        assertThrows(ResponseStatusException.class, () -> service.salvar(dados));
        dados.setTipo("Pizza");
        dados.setAtivo(null);
        assertThrows(ResponseStatusException.class, () -> service.salvar(dados));
        verifyNoInteractions(produtos, pedidos);
    }

    @Test void statusNaoExcluiProdutoNemAlteraPedidos() {
        Produto existente = produto();
        existente.setId("p1");
        when(produtos.findById("p1")).thenReturn(Optional.of(existente));
        service.atualizarStatus("p1", false);
        assertFalse(existente.getAtivo());
        service.atualizarStatus("p1", true);
        assertTrue(existente.getAtivo());
        verify(produtos, times(2)).save(existente);
        verify(produtos, never()).deleteById(any());
        verifyNoInteractions(pedidos);
    }

    @Test void bloqueiaExclusaoDeProdutoVinculado() {
        when(produtos.findById("p1")).thenReturn(Optional.of(produto()));
        when(pedidos.existsByItensProdutoId("p1")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.excluir("p1")).getStatusCode());
        verify(produtos, never()).deleteById(any());
        verify(pedidos, never()).save(any());
        verify(pedidos, never()).deleteById(any());
    }

    @Test void excluiSomenteProdutoSemVinculo() {
        when(produtos.findById("p1")).thenReturn(Optional.of(produto()));
        service.excluir("p1");
        verify(produtos).deleteById("p1");
        verify(pedidos, never()).save(any());
        verify(pedidos, never()).deleteById(any());
    }

    @Test void backendRecusaProdutoInativoEmNovosItens() {
        PedidoService pedidoService = new PedidoService(pedidos, null, produtos, null, null, null);
        Produto inativo = produto();
        inativo.setAtivo(false);
        when(produtos.findById("p1")).thenReturn(Optional.of(inativo));
        Pedido.ItemPedido item = new Pedido.ItemPedido();
        item.setProdutoId("p1");
        item.setQuantidade(1);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(pedidoService, "calcularItens", List.of(item))).getStatusCode());
        verifyNoInteractions(pedidos);
    }
}
