package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/dashboard")
    public String pagina(Model model, org.springframework.security.core.Authentication authentication) {
        model.addAttribute("administrador", authentication.getName());
        model.addAttribute("content", "dashboard :: content");
        model.addAttribute("podeAdministrarUsuarios", true);
        model.addAttribute("podeGerenciarClientes", false);
        model.addAttribute("podeGerenciarPedidos", false);
        model.addAttribute("podeVisualizarAuditoria", true);
        return "layout-adminlte";
    }

    @GetMapping({"/api/admin/dashboard", "/api/admin/dashboard/indicadores", "/api/admin/dashboard/vendas",
            "/api/admin/dashboard/status", "/api/admin/dashboard/produtos"})
    @ResponseBody
    public Object consultar(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status, @RequestParam(required = false) String tipo,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var resumo = service.consultar(service.filtro(inicio, fim, status, tipo));
        return switch (request.getServletPath()) {
            case "/api/admin/dashboard/indicadores" -> resumo.indicadores();
            case "/api/admin/dashboard/vendas" -> resumo.vendas();
            case "/api/admin/dashboard/status" -> resumo.situacao();
            case "/api/admin/dashboard/produtos" -> resumo.produtos();
            default -> resumo;
        };
    }
}
