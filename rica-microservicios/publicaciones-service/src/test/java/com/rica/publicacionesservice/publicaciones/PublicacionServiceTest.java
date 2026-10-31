package com.rica.publicacionesservice.publicaciones;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PublicacionServiceTest {

    @Mock
    private PublicacionRepository publicacionRepository;

    @Mock
    private VerificadorInvestigador verificadorInvestigador;

    @Mock
    private LimitePublicacionesAnualesService limitePublicacionesAnualesService;

    @Mock
    private PublicadorEventosPublicaciones publicadorEventos;

    @InjectMocks
    private PublicacionService publicacionService;

    private Publicacion nuevaPublicacion() {
        return new Publicacion(null, "ana.torres@uptc.edu.co", "Arquitecturas evolutivas", "articulo", 2026, Map.of());
    }

    @Test
    void registrarPublicaElEventoDespuesDeGuardar() {
        Publicacion entrada = nuevaPublicacion();
        Publicacion guardada = new Publicacion("abc123", "ana.torres@uptc.edu.co", "Arquitecturas evolutivas", "articulo", 2026, Map.of());
        when(verificadorInvestigador.existe("ana.torres@uptc.edu.co")).thenReturn(true);
        when(limitePublicacionesAnualesService.puedeRegistrar("ana.torres@uptc.edu.co", 2026)).thenReturn(true);
        when(publicacionRepository.save(entrada)).thenReturn(guardada);

        Publicacion resultado = publicacionService.registrar(entrada);

        assertThat(resultado.getId()).isEqualTo("abc123");
        verify(publicadorEventos).publicacionRegistrada(guardada);
    }

    @Test
    void registrarNoPublicaEventoSiElInvestigadorNoExiste() {
        when(verificadorInvestigador.existe("nadie@uptc.edu.co")).thenReturn(false);
        Publicacion entrada = new Publicacion(null, "nadie@uptc.edu.co", "X", "libro", 2026, Map.of());

        try {
            publicacionService.registrar(entrada);
        } catch (RuntimeException esperado) {
            // el comando falla: no hay hecho que anunciar
        }

        verify(publicadorEventos, never()).publicacionRegistrada(any());
    }

}
