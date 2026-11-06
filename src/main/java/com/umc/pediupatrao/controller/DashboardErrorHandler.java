package com.umc.pediupatrao.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardErrorHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> filtroInvalido(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).header("Cache-Control", "no-store")
                .body(Map.of("erro", exception.getReason() == null ? "Consulta inválida." : exception.getReason()));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> indisponivel(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store")
                .body(Map.of("erro", "Não foi possível consultar os pedidos. Tente novamente mais tarde."));
    }
}
