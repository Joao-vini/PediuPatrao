package com.umc.pediupatrao.entity;

import jakarta.persistence.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

@Document(collection = "pedidos")
@Entity
public class Pedido {
    @Id
    private String id;
    private String clienteId;
    private String tipoPizza;
    private String status;
    private int quantidade;
    private String sabor;

    private TipoPedido tipoPedido;
    private String clienteNome;
    private String clienteTelefone;
    private String enderecoEntrega;
    private BigDecimal subtotalProdutos;
    private BigDecimal taxaEntrega;
    private TipoDesconto tipoDesconto;
    private BigDecimal descontoInformado;
    private FormaPagamento formaPagamento;
    private BigDecimal trocoPara;
    private BigDecimal valorTroco;
    private String observacaoGeral;
    private String motivoCancelamento;
    private Instant canceladoEm;
    private String canceladoPorId;
    private String canceladoPorUsername;

    // Dados adicionados no RF02; os campos legados acima permanecem para documentos antigos.
    private List<ItemPedido> itens;
    private BigDecimal descontoPercentual;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;
    private String usuarioResponsavelId;
    private String usuarioResponsavelUsername;
    private Instant dataHora;
    private Instant dataHoraSaida;
    private String usuarioSaidaId;
    private String usuarioSaidaUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getTipoPizza() {
        return tipoPizza;
    }

    public void setTipoPizza(String tipoPizza) {
        this.tipoPizza = tipoPizza;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public String getSabor() {
        return sabor;
    }

    public void setSabor(String sabor) {
        this.sabor = sabor;
    }

    public TipoPedido getTipoPedido() { return tipoPedido; }
    public void setTipoPedido(TipoPedido tipoPedido) { this.tipoPedido = tipoPedido; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public String getClienteTelefone() { return clienteTelefone; }
    public void setClienteTelefone(String clienteTelefone) { this.clienteTelefone = clienteTelefone; }
    public String getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }
    public BigDecimal getSubtotalProdutos() { return subtotalProdutos; }
    public void setSubtotalProdutos(BigDecimal subtotalProdutos) { this.subtotalProdutos = subtotalProdutos; }
    public BigDecimal getTaxaEntrega() { return taxaEntrega; }
    public void setTaxaEntrega(BigDecimal taxaEntrega) { this.taxaEntrega = taxaEntrega; }
    public TipoDesconto getTipoDesconto() { return tipoDesconto; }
    public void setTipoDesconto(TipoDesconto tipoDesconto) { this.tipoDesconto = tipoDesconto; }
    public BigDecimal getDescontoInformado() { return descontoInformado; }
    public void setDescontoInformado(BigDecimal descontoInformado) { this.descontoInformado = descontoInformado; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }
    public BigDecimal getTrocoPara() { return trocoPara; }
    public void setTrocoPara(BigDecimal trocoPara) { this.trocoPara = trocoPara; }
    public BigDecimal getValorTroco() { return valorTroco; }
    public void setValorTroco(BigDecimal valorTroco) { this.valorTroco = valorTroco; }
    public String getObservacaoGeral() { return observacaoGeral; }
    public void setObservacaoGeral(String observacaoGeral) { this.observacaoGeral = observacaoGeral; }
    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }
    public Instant getCanceladoEm() { return canceladoEm; }
    public void setCanceladoEm(Instant canceladoEm) { this.canceladoEm = canceladoEm; }
    public String getCanceladoPorId() { return canceladoPorId; }
    public void setCanceladoPorId(String canceladoPorId) { this.canceladoPorId = canceladoPorId; }
    public String getCanceladoPorUsername() { return canceladoPorUsername; }
    public void setCanceladoPorUsername(String canceladoPorUsername) { this.canceladoPorUsername = canceladoPorUsername; }

    public enum TipoPedido {
        ENTREGA("Entrega"), RETIRADA("Retirada"), BALCAO("Balcão");

        private final String descricao;
        TipoPedido(String descricao) { this.descricao = descricao; }

        public String getDescricao() {
            return descricao;
        }
    }

    public enum TipoDesconto {
        SEM_DESCONTO, PERCENTUAL,
        // Apenas para leitura de documentos antigos; rejeitado nas operações de desconto.
        @Deprecated VALOR
    }

    public enum FormaPagamento {
        DINHEIRO("Dinheiro"), PIX("Pix"), CARTAO_DEBITO("Cartão de débito"),
        CARTAO_CREDITO("Cartão de crédito"), VALE_REFEICAO("Vale-refeição");

        private final String descricao;
        FormaPagamento(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getDescontoPercentual() {
        return descontoPercentual;
    }

    public void setDescontoPercentual(BigDecimal descontoPercentual) {
        this.descontoPercentual = descontoPercentual;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getUsuarioResponsavelId() {
        return usuarioResponsavelId;
    }

    public void setUsuarioResponsavelId(String usuarioResponsavelId) {
        this.usuarioResponsavelId = usuarioResponsavelId;
    }

    public String getUsuarioResponsavelUsername() {
        return usuarioResponsavelUsername;
    }

    public void setUsuarioResponsavelUsername(String usuarioResponsavelUsername) {
        this.usuarioResponsavelUsername = usuarioResponsavelUsername;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public void setDataHora(Instant dataHora) {
        this.dataHora = dataHora;
    }

    public Instant getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(Instant dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }
    public String getUsuarioSaidaId() { return usuarioSaidaId; }
    public void setUsuarioSaidaId(String usuarioSaidaId) { this.usuarioSaidaId = usuarioSaidaId; }
    public String getUsuarioSaidaUsername() { return usuarioSaidaUsername; }
    public void setUsuarioSaidaUsername(String usuarioSaidaUsername) { this.usuarioSaidaUsername = usuarioSaidaUsername; }

    public static class ItemPedido {
        private String produtoId;
        private String nomeProduto;
        @JsonDeserialize(using = QuantidadeInteiraDeserializer.class)
        private int quantidade;
        private BigDecimal precoUnitario;
        private String observacao;

        public String getProdutoId() {
            return produtoId;
        }

        public void setProdutoId(String produtoId) {
            this.produtoId = produtoId;
        }

        public String getNomeProduto() {
            return nomeProduto;
        }

        public void setNomeProduto(String nomeProduto) {
            this.nomeProduto = nomeProduto;
        }

        public int getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(int quantidade) {
            this.quantidade = quantidade;
        }

        public BigDecimal getPrecoUnitario() {
            return precoUnitario;
        }

        public void setPrecoUnitario(BigDecimal precoUnitario) {
            this.precoUnitario = precoUnitario;
        }

        public BigDecimal getSubtotal() {
            return precoUnitario == null ? null : precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        }

        public String getObservacao() { return observacao; }
        public void setObservacao(String observacao) { this.observacao = observacao; }
    }

    // Restrição apenas da entrada JSON dos itens; não altera leitura BSON nem campos legados.
    public static final class QuantidadeInteiraDeserializer extends StdDeserializer<Integer> {
        public QuantidadeInteiraDeserializer() { super(Integer.class); }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "A quantidade do item deve ser um número inteiro.");
            }
            return parser.getIntValue();
        }
    }
    
    
}
