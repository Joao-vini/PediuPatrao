package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private static final List<String> TIPOS = List.of("Pizza", "Esfiha", "Lanche", "Bebida", "Porção", "Adicional");

    public List<String> tiposPermitidos() { return TIPOS; }

    public List<Produto> listarAtivos() { return produtoRepository.findByAtivoTrue(); }

    public Produto buscarPorId(String id) {
        return produtoRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }

    public Produto novoProduto(Produto produto) {
        if (produto.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cadastro não deve informar ID.");
        }
        return salvar(produto);
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    public void excluir(String id) {
        buscarPorId(id);
        // Preserva também pedidos antigos com dados incompletos do produto.
        if (pedidoRepository.existsByItensProdutoId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este produto foi utilizado em pedidos e não pode ser excluído. Desative-o para preservar o histórico.");
        }
        produtoRepository.deleteById(id);
    }


    public Produto salvar(Produto produto) {
        validar(produto);
        Produto destino = produto.getId() == null ? new Produto() : buscarPorId(produto.getId());
        destino.setNome(produto.getNome().trim());
        destino.setTipo(produto.getTipo().trim());
        destino.setPreco(produto.getPreco());
        destino.setAtivo(produto.getAtivo());
        return produtoRepository.save(destino);
    }

    public Produto atualizarStatus(String id, boolean ativo) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(ativo);
        return produtoRepository.save(produto);
    }

    private void validar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank() || produto.getNome().trim().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome de até 150 caracteres.");
        }
        if (produto.getTipo() == null || !TIPOS.contains(produto.getTipo().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um tipo de produto válido.");
        }
        Double preco = produto.getPreco();
        if (preco == null || !Double.isFinite(preco) || preco < 0
                || BigDecimal.valueOf(preco).stripTrailingZeros().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um preço não negativo com até duas casas decimais.");
        }
        if (produto.getAtivo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o status ativo do produto.");
        }
    }
}
