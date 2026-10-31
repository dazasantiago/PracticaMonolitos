package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.compartido.RecursoNoEncontradoException;
import com.rica.investigadoresservice.investigadores.dominio.CorreoDuplicadoException;
import com.rica.investigadoresservice.investigadores.dominio.CorreoInstitucional;
import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigadorServiceTest {

    @Mock
    private RepositorioInvestigadores repositorioInvestigadores;

    @Mock
    private InvestigadorFactory investigadorFactory;

    @Mock
    private PublicadorEventos publicadorEventos;

    @InjectMocks
    private InvestigadorService investigadorService;

    @Test
    void buscarPorIdDevuelveElInvestigadorCuandoExiste() {
        Investigador investigador = new Investigador(1L, "Ana Torres", new CorreoInstitucional("ana.torres@uptc.edu.co"), "GIT-UPTC");
        when(repositorioInvestigadores.buscarPorId(1L)).thenReturn(Optional.of(investigador));

        Investigador resultado = investigadorService.buscarPorId(1L);

        assertThat(resultado.getNombreCompleto()).isEqualTo("Ana Torres");
    }

    @Test
    void buscarPorIdLanzaExcepcionCuandoNoExiste() {
        when(repositorioInvestigadores.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investigadorService.buscarPorId(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void registrarPropagaCorreoInstitucionalDuplicadoDeLaFactory() {
        when(investigadorFactory.crear("Carlos Ruiz", "carlos.ruiz@uptc.edu.co", "GIT-UPTC"))
                .thenThrow(new CorreoDuplicadoException(
                        "Ya existe un investigador registrado con el correo carlos.ruiz@uptc.edu.co"));

        assertThatThrownBy(() -> investigadorService.registrar("Carlos Ruiz", "carlos.ruiz@uptc.edu.co", "GIT-UPTC"))
                .isInstanceOf(CorreoDuplicadoException.class);

        verify(investigadorFactory).crear("Carlos Ruiz", "carlos.ruiz@uptc.edu.co", "GIT-UPTC");
    }

    @Test
    void rechazaCorreoFueraDelDominioUptc() {
        assertThatThrownBy(() -> new CorreoInstitucional("carlos@gmail.com"))
            .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void confirmarEliminacionBorraAlInvestigadorYPublicaElEventoEliminado() {
        Investigador enEliminacion = new Investigador(1L, "Ana Torres", new CorreoInstitucional("ana.torres@uptc.edu.co"), "GIT-UPTC");
        enEliminacion.marcarEnEliminacion();
        when(repositorioInvestigadores.buscarPorCorreo("ana.torres@uptc.edu.co")).thenReturn(Optional.of(enEliminacion));

        investigadorService.confirmarEliminacion("ana.torres@uptc.edu.co");

        verify(repositorioInvestigadores).eliminar(enEliminacion);
        verify(publicadorEventos).publicar(
                "investigadores.eliminados",
                "ana.torres@uptc.edu.co",
                java.util.Map.of("correoInstitucional", "ana.torres@uptc.edu.co"));
    }
}
