package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuditoriaService {
    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
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
        auditoria.setValoresAnteriores(anteriores == null ? Map.of() : Map.copyOf(anteriores));
        auditoria.setValoresNovos(novos == null ? Map.of() : Map.copyOf(novos));
        List<String> campos = new ArrayList<>(auditoria.getValoresAnteriores().keySet());
        for (String campo : auditoria.getValoresNovos().keySet()) {
            if (!campos.contains(campo)) campos.add(campo);
        }
        auditoria.setCamposAlterados(campos);
        auditoria.setJustificativa(justificativa);
        auditoriaRepository.save(auditoria);
    }

    public List<Auditoria> listar() {
        return auditoriaRepository.findAll();
    }

    private String perfil(Authentication authentication) {
        if (authentication == null) return "SISTEMA";
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5))
                .findFirst().orElse("DESCONHECIDO");
    }
}
