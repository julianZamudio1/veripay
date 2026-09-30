package com.veripay.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reenvía las rutas del frontend Angular (p. ej. /clientes/5) a index.html
 * para que el router del lado del cliente las resuelva al recargar la página.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/login", "/tablero", "/clientes", "/clientes/**", "/cuentas", "/cuentas/**",
            "/transferencias", "/auditoria"})
    public String spa() {
        return "forward:/index.html";
    }
}
