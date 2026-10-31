package com.rica.investigadoresservice.investigadores.aplicacion;

import com.rica.investigadoresservice.compartido.RecursoNoEncontradoException;
import com.rica.investigadoresservice.investigadores.dominio.Investigador;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InvestigadorService implements InvestigadorUseCase {

    private final RepositorioInvestigadores repositorioInvestigadores;
    private final InvestigadorFactory investigadorFactory;
    private final PublicadorEventos publicadorEventos;

    public InvestigadorService(RepositorioInvestigadores repositorioInvestigadores, InvestigadorFactory investigadorFactory,
                               PublicadorEventos publicadorEventos) {
        this.repositorioInvestigadores = repositorioInvestigadores;
        this.investigadorFactory = investigadorFactory;
        this.publicadorEventos = publicadorEventos;
    }

    @Override
    public List<Investigador> listarTodos() {
        return repositorioInvestigadores.listarTodos();
    }

    @Override
    public Investigador buscarPorId(Long id) {
        return repositorioInvestigadores.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un investigador con id " + id));
    }

    @Override
    @Transactional
    public Investigador registrar(String nombreCompleto, String correoInstitucional, String grupoInvestigacion) {
        Investigador investigador = investigadorFactory.crear(nombreCompleto, correoInstitucional, grupoInvestigacion);
        Investigador guardado = repositorioInvestigadores.guardar(investigador);

        // Transactional Outbox: el evento se guarda en la MISMA transacción que el investigador.
        publicadorEventos.publicar(
                "investigadores.registrados",
                correoInstitucional,
                Map.of("id", guardado.getId(),
                       "correoInstitucional", correoInstitucional,
                       "nombreCompleto", nombreCompleto));
        return guardado;
    }

    @Override
    @Transactional
    public void iniciarEliminacion(Long id) {
        Investigador investigador = buscarPorId(id);
        if (investigador.estaEnEliminacion()) {
            return; // idempotente: la Saga ya está en curso
        }
        investigador.marcarEnEliminacion();
        repositorioInvestigadores.guardar(investigador);

        String correo = investigador.getCorreoInstitucional().valor();
        publicadorEventos.publicar(
                "investigadores.en-eliminacion",
                correo,
                Map.of("correoInstitucional", correo));
    }

    @Override
    @Transactional
    public void confirmarEliminacion(String correoInstitucional) {
        repositorioInvestigadores.buscarPorCorreo(correoInstitucional)
                .filter(Investigador::estaEnEliminacion)
                .ifPresent(investigador -> {
                    repositorioInvestigadores.eliminar(investigador);
                    // Los consumidores (p. ej. consultas-service) se enteran de que ya no existe.
                    publicadorEventos.publicar(
                            "investigadores.eliminados",
                            correoInstitucional,
                            Map.of("correoInstitucional", correoInstitucional));
                });
    }

}
