package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigadorServiceConFalsoTest {

    @Test
    void registraYRecuperaUnInvestigadorSinNingunaDependenciaDeSpringNiDeBaseDeDatos() {
        RepositorioInvestigadoresFalso repositorio = new RepositorioInvestigadoresFalso();
        InvestigadorFactory factory = new InvestigadorFactory(repositorio, evento -> { });
        InvestigadorService service = new InvestigadorService(repositorio, factory);

        Investigador guardado = service.registrar("Ana Torres", "ana.torres@uptc.edu.co", "GIT-UPTC");

        assertThat(guardado.getId()).isNotNull();
        assertThat(service.buscarPorId(guardado.getId()).getNombreCompleto()).isEqualTo("Ana Torres");
    }
}
