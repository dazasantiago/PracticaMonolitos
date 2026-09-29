package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.investigadores.dominio.CorreoDuplicadoException;
import com.rica.investigadoresservice.investigadores.dominio.CorreoInstitucional;
import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import com.rica.investigadoresservice.investigadores.dominio.InvestigadorRegistrado;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InvestigadorFactory {

    private final RepositorioInvestigadores repositorioInvestigadores;
    private final ApplicationEventPublisher eventPublisher;

    public InvestigadorFactory(RepositorioInvestigadores repositorioInvestigadores, ApplicationEventPublisher eventPublisher) {
        this.repositorioInvestigadores = repositorioInvestigadores;
        this.eventPublisher = eventPublisher;
    }

    public Investigador crear(String nombreCompleto, String correoInstitucional, String grupoInvestigacion) {
        CorreoInstitucional correo = new CorreoInstitucional(correoInstitucional);

        if (repositorioInvestigadores.existeCorreo(correo.valor())) {
            throw new CorreoDuplicadoException(
                    "Ya existe un investigador registrado con el correo " + correo.valor());
        }

        Investigador investigador = new Investigador(null, nombreCompleto, correo, grupoInvestigacion);
        eventPublisher.publishEvent(new InvestigadorRegistrado(correo.valor(), Instant.now()));
        return investigador;
    }
}
