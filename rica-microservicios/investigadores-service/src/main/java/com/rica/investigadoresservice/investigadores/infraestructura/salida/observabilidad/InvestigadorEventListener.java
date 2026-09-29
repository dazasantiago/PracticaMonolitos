package com.rica.investigadoresservice.investigadores.infraestructura.salida.observabilidad;

import com.rica.investigadoresservice.investigadores.dominio.InvestigadorRegistrado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InvestigadorEventListener {

    private static final Logger log = LoggerFactory.getLogger(InvestigadorEventListener.class);

    @EventListener
    public void onInvestigadorRegistrado(InvestigadorRegistrado evento) {
        log.info("InvestigadorRegistrado: correo={} ocurridoEn={}", evento.correoInstitucional(), evento.ocurridoEn());
    }

}
