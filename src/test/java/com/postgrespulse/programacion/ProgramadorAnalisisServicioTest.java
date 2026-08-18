package com.postgrespulse.programacion;

import com.postgrespulse.analisis.OrquestadorAnalisisServicio;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * docs/SPECS.md #12 (Resiliencia): "una fuente que falla no debe cortar el
 * ciclo global" -- ProgramadorAnalisisServicio no tenia pruebas propias, solo
 * se ejercitaba indirectamente via el circuit breaker del orquestador.
 */
@ExtendWith(MockitoExtension.class)
class ProgramadorAnalisisServicioTest {

    @Mock
    private FuenteDatosRepositorio fuenteDatosRepositorio;

    @Mock
    private OrquestadorAnalisisServicio orquestador;

    private ProgramadorAnalisisServicio programador;

    @BeforeEach
    void configurar() {
        programador = new ProgramadorAnalisisServicio(fuenteDatosRepositorio, orquestador);
    }

    private FuenteDatos fuente(long id) {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(id);
        return fuente;
    }

    @Test
    void analizaTodasLasFuentesHabilitadasDevueltasPorElRepositorio() {
        when(fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc())
                .thenReturn(List.of(fuente(1L), fuente(2L), fuente(3L)));

        programador.ejecutarCiclo();

        verify(orquestador).ejecutarAnalisis(1L, TipoDisparo.PROGRAMADO);
        verify(orquestador).ejecutarAnalisis(2L, TipoDisparo.PROGRAMADO);
        verify(orquestador).ejecutarAnalisis(3L, TipoDisparo.PROGRAMADO);
    }

    @Test
    void unaFuenteQueFallaNoDetieneElCicloDeLasDemas() {
        when(fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc())
                .thenReturn(List.of(fuente(1L), fuente(2L)));
        when(orquestador.ejecutarAnalisis(1L, TipoDisparo.PROGRAMADO))
                .thenThrow(new RuntimeException("fuente caida"));
        when(orquestador.ejecutarAnalisis(2L, TipoDisparo.PROGRAMADO))
                .thenReturn(new Analisis());

        programador.ejecutarCiclo();

        verify(orquestador).ejecutarAnalisis(2L, TipoDisparo.PROGRAMADO);
    }

    @Test
    void siNoHayFuentesHabilitadasNoInvocaAlOrquestador() {
        when(fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc()).thenReturn(List.of());

        programador.ejecutarCiclo();

        verify(orquestador, times(0)).ejecutarAnalisis(anyLong(), eq(TipoDisparo.PROGRAMADO));
    }

    @Test
    void siempreDisparaConTipoProgramado() {
        when(fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc())
                .thenReturn(List.of(fuente(5L)));

        programador.ejecutarCiclo();

        ArgumentCaptor<TipoDisparo> captor = ArgumentCaptor.forClass(TipoDisparo.class);
        verify(orquestador).ejecutarAnalisis(eq(5L), captor.capture());
        assertThat(captor.getValue()).isEqualTo(TipoDisparo.PROGRAMADO);
    }
}
