package com.umc.pediupatrao.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "auditorias")
public class Auditoria {
    @Id
    private String id;
    private String usuario;
    private String perfil;
    private Instant dataHora;
    private String operacao;
    private String entidade;
    private String entidadeId;
    private List<String> camposAlterados;
    private Map<String, String> valoresAnteriores;
    private Map<String, String> valoresNovos;
    private String justificativa;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public Instant getDataHora() { return dataHora; }
    public void setDataHora(Instant dataHora) { this.dataHora = dataHora; }
    public String getOperacao() { return operacao; }
    public void setOperacao(String operacao) { this.operacao = operacao; }
    public String getEntidade() { return entidade; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public String getEntidadeId() { return entidadeId; }
    public void setEntidadeId(String entidadeId) { this.entidadeId = entidadeId; }
    public List<String> getCamposAlterados() { return camposAlterados; }
    public void setCamposAlterados(List<String> camposAlterados) { this.camposAlterados = camposAlterados; }
    public Map<String, String> getValoresAnteriores() { return valoresAnteriores; }
    public void setValoresAnteriores(Map<String, String> valoresAnteriores) { this.valoresAnteriores = valoresAnteriores; }
    public Map<String, String> getValoresNovos() { return valoresNovos; }
    public void setValoresNovos(Map<String, String> valoresNovos) { this.valoresNovos = valoresNovos; }
    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
}
