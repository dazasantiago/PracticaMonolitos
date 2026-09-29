package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.investigadores.dominio.CorreoDuplicadoException;
import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import com.rica.investigadoresservice.investigadores.dominio.InvestigadorRegistrado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigadorFactoryTest {

    @Mock
    private RepositorioInvestigadores repositorioInvestigadores;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InvestigadorFactory investigadorFactory;

    @Test
    void creaUnInvestigadorCuandoElCorreoNoEstaRegistrado() {
        when(repositorioInvestigadores.existeCorreo("ana.torres@uptc.edu.co")).thenReturn(false);

        Investigador investigador = investigadorFactory.crear("Ana Torres", "ana.torres@uptc.edu.co", "GIT-UPTC");

        assertThat(investigador.getId()).isNull();
        assertThat(investigador.getNombreCompleto()).isEqualTo("Ana Torres");
        assertThat(investigador.getCorreoInstitucional().valor()).isEqualTo("ana.torres@uptc.edu.co");
        assertThat(investigador.getGrupoInvestigacion()).isEqualTo("GIT-UPTC");
        verify(eventPublisher).publishEvent(any(InvestigadorRegistrado.class));
    }

    @Test
    void rechazaCorreoInstitucionalDuplicado() {
        when(repositorioInvestigadores.existeCorreo("carlos.ruiz@uptc.edu.co")).thenReturn(true);

        assertThatThrownBy(() -> investigadorFactory.crear("Carlos Ruiz", "carlos.ruiz@uptc.edu.co", "GIT-UPTC"))
                .isInstanceOf(CorreoDuplicadoException.class);

        verify(repositorioInvestigadores).existeCorreo("carlos.ruiz@uptc.edu.co");
    }

    @Test
    void rechazaCorreoFueraDelDominioUptcAntesDeConsultarElRepositorio() {
        assertThatThrownBy(() -> investigadorFactory.crear("Carlos Ruiz", "carlos@gmail.com", "GIT-UPTC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
