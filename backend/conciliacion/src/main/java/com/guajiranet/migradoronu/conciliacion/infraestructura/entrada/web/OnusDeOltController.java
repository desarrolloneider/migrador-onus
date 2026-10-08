package com.guajiranet.migradoronu.conciliacion.infraestructura.entrada.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ConsultarOnusDeOlt;
import com.guajiranet.migradoronu.conciliacion.aplicacion.ConsultarOnusDeOlt.OnusDeOlt;

/** Las ONUs de una OLT con su estado y su perfil TR-069 real, para la pantalla de nuevo lote. */
@RestController
public class OnusDeOltController {

    private final ConsultarOnusDeOlt consulta;

    public OnusDeOltController(ConsultarOnusDeOlt consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/api/v1/olts/{codigo}/onus")
    public OnusDeOlt onus(@PathVariable String codigo) {
        return consulta.consultar(codigo);
    }
}
