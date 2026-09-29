package com.rica.investigadoresservice.investigadores.infraestructura.entrada.web;

import com.rica.investigadoresservice.investigadores.dominio.Investigador;

public class InvestigadorMapper {

    private InvestigadorMapper() {
    }

    public static InvestigadorResponse aResponse(Investigador investigador) {
        return new InvestigadorResponse(
                investigador.getId(),
                investigador.getNombreCompleto(),
                investigador.getGrupoInvestigacion()
        );
    }

}
