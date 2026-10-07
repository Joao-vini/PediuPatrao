package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente, Authentication authentication) {
        return salvar(cliente, authentication);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id, Authentication authentication) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "A exclusão de clientes não é autorizada pelo RF01.");
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                    .noneMatch(authority -> "ROLE_ATENDENTE".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente ATENDENTE pode cadastrar ou atualizar clientes.");
        }
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(authentication, "CRIACAO", "CLIENTE", salvo.getId(),
                    Map.of(), snapshot(salvo), null);
            return salvo;
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            if (clienteRepository.existsById(cliente.getId())) {
                Cliente anterior = clienteRepository.findById(cliente.getId()).orElseThrow();
                Map<String, String> valoresAnteriores = snapshot(anterior);
                Cliente salvo = clienteRepository.save(cliente);
                Map<String, String> valoresNovos = snapshot(salvo);
                valoresAnteriores.keySet().removeIf(campo -> valoresAnteriores.get(campo)
                        .equals(valoresNovos.get(campo)));
                valoresNovos.keySet().retainAll(valoresAnteriores.keySet());
                if (!valoresNovos.isEmpty()) {
                    auditoriaService.registrar(authentication, "ALTERACAO", "CLIENTE", salvo.getId(),
                            valoresAnteriores, valoresNovos, null);
                }
                return salvo;
            } else {
                throw new IllegalArgumentException("Cliente não encontrado para atualização.");
            }
        }
    }

    private Map<String, String> snapshot(Cliente cliente) {
        Map<String, String> dados = new LinkedHashMap<>();
        adicionar(dados, "nome", cliente.getNome());
        adicionar(dados, "telefone", cliente.getTelefone());
        adicionar(dados, "cep", cliente.getCep());
        adicionar(dados, "logradouro", cliente.getLogradouro());
        adicionar(dados, "numero", cliente.getNumero());
        adicionar(dados, "complemento", cliente.getComplemento());
        adicionar(dados, "bairro", cliente.getBairro());
        adicionar(dados, "cidade", cliente.getCidade());
        adicionar(dados, "estado", cliente.getEstado());
        adicionar(dados, "endereco", cliente.getEndereco());
        return dados;
    }

    private void adicionar(Map<String, String> dados, String campo, String valor) {
        dados.put(campo, valor == null ? "" : valor);
    }

}
