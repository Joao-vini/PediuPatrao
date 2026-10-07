package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditoriaService {
    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(FUSO);
    private static final Map<String, String> OPERACOES = Map.ofEntries(
            Map.entry("CRIACAO", "Criação"), Map.entry("ALTERACAO", "Alteração"),
            Map.entry("EXCLUSAO", "Exclusão"), Map.entry("PEDIDO_CRIADO", "Pedido criado"),
            Map.entry("STATUS_PEDIDO_ALTERADO", "Status do pedido alterado"),
            Map.entry("PEDIDO_CANCELADO", "Pedido cancelado"), Map.entry("PEDIDO_EXCLUIDO", "Pedido excluído"),
            Map.entry("DESCONTO_APLICADO", "Desconto aplicado"), Map.entry("USUARIO_ATIVADO", "Usuário ativado"),
            Map.entry("USUARIO_DESATIVADO", "Usuário desativado"), Map.entry("USUARIO_EXCLUIDO", "Usuário excluído"));
    private static final Pattern CAMPO_SENSIVEL = Pattern.compile(
            "senha|password|passwd|pwd|hash|credencia(?:l|is)|credential|token|secret|segredo|authorization|authentication|apikey|accesskey|privatekey|chaveprivada|cookie|session|sessao");
    private static final Pattern VALOR_SENSIVEL = Pattern.compile(
            "(?i)(?:senha|password|passwd|pwd|credencia(?:l|is)|token|secret|api[_-]?key|authorization)\\s*[\\\"']?\\s*[:=]"
            + "|\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}|\\$(?:argon2|scrypt|pbkdf2)[^\\s]*"
            + "|\\bbearer\\s+\\S+|\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
            + "|\\b(?:[a-f0-9]{32}|[a-f0-9]{40}|[a-f0-9]{64})\\b|-----BEGIN .*PRIVATE KEY-----");

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public void registrar(Authentication authentication, String operacao, String entidade,
                          String entidadeId, Map<String, String> anteriores,
                          Map<String, String> novos, String justificativa) {
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(authentication == null ? "sistema" : authentication.getName());
        auditoria.setPerfil(perfil(authentication));
        auditoria.setDataHora(Instant.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        // A mesma proteção da consulta também se aplica antes da persistência.
        auditoria.setValoresAnteriores(Map.copyOf(mapaSeguro(anteriores)));
        auditoria.setValoresNovos(Map.copyOf(mapaSeguro(novos)));
        List<String> campos = new ArrayList<>(auditoria.getValoresAnteriores().keySet());
        for (String campo : auditoria.getValoresNovos().keySet()) {
            if (!campos.contains(campo)) campos.add(campo);
        }
        campos.removeIf(campo -> auditoria.getValoresAnteriores().containsKey(campo)
                && auditoria.getValoresNovos().containsKey(campo)
                && java.util.Objects.equals(auditoria.getValoresAnteriores().get(campo),
                        auditoria.getValoresNovos().get(campo)));
        auditoria.setCamposAlterados(campos);
        auditoria.setJustificativa(textoSeguro(justificativa));
        auditoriaRepository.save(auditoria);
    }

    public List<Auditoria> listar() {
        return auditoriaRepository.findAll();
    }

    public Map<String, String> operacoesDisponiveis() {
        return new java.util.TreeMap<>(OPERACOES);
    }

    public Page<RegistroConsulta> consultar(String usuario, String operacao, String entidade,
            String dataInicial, String dataFinal, int pagina) {
        if (pagina < 0) throw invalido("A página deve ser não negativa.");
        LocalDate inicio = dataFiltro(dataInicial);
        LocalDate fim = dataFiltro(dataFinal);
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw invalido("A data inicial não pode ser posterior à data final.");
        }
        Query query = new Query();
        if (preenchido(usuario)) query.addCriteria(Criteria.where("usuario").regex(Pattern.quote(usuario.trim()), "i"));
        if (preenchido(operacao)) query.addCriteria(Criteria.where("operacao").is(operacao.trim()));
        if (preenchido(entidade)) query.addCriteria(Criteria.where("entidade").is(entidade.trim()));
        if (inicio != null || fim != null) {
            Criteria periodo = Criteria.where("dataHora");
            if (inicio != null) periodo.gte(inicio.atStartOfDay(FUSO).toInstant());
            if (fim != null) periodo.lt(fim.plusDays(1).atStartOfDay(FUSO).toInstant());
            query.addCriteria(periodo);
        }
        long total = mongoTemplate.count(query, Auditoria.class);
        int ultimaPagina = total == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (total - 1) / 20);
        PageRequest pageable = PageRequest.of(Math.min(pagina, ultimaPagina), 20,
                Sort.by(Sort.Order.desc("dataHora"), Sort.Order.desc("id")));
        if (total == 0) return Page.empty(pageable);
        List<RegistroConsulta> registros = mongoTemplate.find(query.with(pageable), Auditoria.class).stream()
                .map(this::paraConsulta).toList();
        return new PageImpl<>(registros, pageable, total);
    }

    private boolean preenchido(String valor) { return valor != null && !valor.isBlank(); }

    private LocalDate dataFiltro(String valor) {
        if (!preenchido(valor)) return null;
        try {
            LocalDate data = LocalDate.parse(valor);
            if (data.getYear() < 1 || data.getYear() > 9998) throw invalido("Informe uma data válida.");
            return data;
        } catch (DateTimeParseException e) {
            throw invalido("Informe datas válidas no formato dia/mês/ano.");
        }
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public RegistroConsulta paraConsulta(Auditoria registro) {
        Map<String, String> anteriores = mapaSeguro(registro.getValoresAnteriores());
        Map<String, String> novos = mapaSeguro(registro.getValoresNovos());
        List<String> campos = registro.getCamposAlterados() == null ? List.of()
                : registro.getCamposAlterados().stream().filter(c -> c != null && !campoSensivel(c))
                        .map(this::textoSeguro).toList();
        String operador = novos.getOrDefault("operador", anteriores.getOrDefault("operador", textoSeguro(registro.getUsuario())));
        String gerente = novos.getOrDefault("gerenteAutorizador", anteriores.get("gerenteAutorizador"));
        return new RegistroConsulta(textoSeguro(registro.getId()), textoSeguro(registro.getUsuario()),
                textoSeguro(registro.getPerfil()), registro.getDataHora() == null ? "Não informada" : DATA.format(registro.getDataHora()),
                descricao(registro.getOperacao()), descricao(registro.getEntidade()), textoSeguro(registro.getEntidadeId()),
                campos, anteriores, novos, textoSeguro(registro.getJustificativa()), operador, gerente);
    }

    private String descricao(String codigo) {
        if (codigo == null || codigo.isBlank()) return "Não informada";
        if (OPERACOES.containsKey(codigo)) return OPERACOES.get(codigo);
        return switch (codigo) {
            case "USUARIO" -> "Usuário";
            case "CLIENTE" -> "Cliente";
            case "PEDIDO" -> "Pedido";
            case "PRODUTO" -> "Produto";
            default -> textoSeguro(codigo.substring(0, 1).toUpperCase(Locale.ROOT)
                    + codigo.substring(1).toLowerCase(Locale.ROOT).replace('_', ' '));
        };
    }

    private boolean campoSensivel(String campo) {
        String normalizado = Normalizer.normalize(campo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return CAMPO_SENSIVEL.matcher(normalizado).find();
    }

    private String textoSeguro(String texto) {
        return texto != null && VALOR_SENSIVEL.matcher(texto).find() ? "[Dado sensível ocultado]" : texto;
    }

    private Map<String, String> mapaSeguro(Map<String, String> origem) {
        Map<String, String> seguro = new LinkedHashMap<>();
        if (origem != null) origem.forEach((campo, valor) -> {
            if (campo != null && !campoSensivel(campo)) seguro.put(textoSeguro(campo), textoSeguro(valor));
        });
        return seguro;
    }

    // Modelo exclusivo de leitura: nenhum objeto sanitizado é salvo no MongoDB.
    public record RegistroConsulta(String id, String usuario, String perfil, String dataHoraFormatada,
            String operacaoDescricao, String entidadeDescricao, String entidadeId, List<String> camposAlterados,
            Map<String, String> valoresAnteriores, Map<String, String> valoresNovos, String justificativa,
            String operador, String gerenteAutorizador) { }

    private String perfil(Authentication authentication) {
        if (authentication == null) return "SISTEMA";
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst().orElse("DESCONHECIDO");
    }
}
