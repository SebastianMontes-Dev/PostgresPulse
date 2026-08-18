package com.postgrespulse.demo;

import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.excepcion.NombreDuplicadoException;
import com.postgrespulse.servicio.FuenteServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * docs/SPECS.md #17 ("un solo comando de Docker Compose + panel de control"):
 * el sembrado debe poder correr en cada arranque del contenedor "app" sin
 * romper cuando la fuente ya fue registrada en un arranque anterior.
 */
@ExtendWith(MockitoExtension.class)
class SembradorFuenteDemoServicioTest {

    @Mock
    private FuenteServicio fuenteServicio;

    private SembradorFuenteDemoServicio sembrador;

    @BeforeEach
    void configurar() {
        sembrador = new SembradorFuenteDemoServicio(fuenteServicio, "target-demo", 5432);
    }

    @Test
    void registraLaFuenteDemoConLosDatosDeTargetDemo() {
        sembrador.run(null);

        ArgumentCaptor<CrearFuenteDto> captor = ArgumentCaptor.forClass(CrearFuenteDto.class);
        verify(fuenteServicio).crear(captor.capture());

        CrearFuenteDto dto = captor.getValue();
        assertThat(dto.nombre()).isEqualTo(SembradorFuenteDemoServicio.NOMBRE_FUENTE_DEMO);
        assertThat(dto.host()).isEqualTo("target-demo");
        assertThat(dto.puerto()).isEqualTo(5432);
        assertThat(dto.baseDeDatos()).isEqualTo("ventas_db");
        assertThat(dto.usuario()).isEqualTo("demo");
        assertThat(dto.contrasena()).isEqualTo("demo");
    }

    @Test
    void esIdempotenteSiLaFuenteYaExiste() {
        when(fuenteServicio.crear(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new NombreDuplicadoException(SembradorFuenteDemoServicio.NOMBRE_FUENTE_DEMO));

        assertThatCode(() -> sembrador.run(null)).doesNotThrowAnyException();
    }

    @Test
    void ejecutarDosVecesSoloIntentaCrearDosVeces() {
        FuenteRespuestaDto creada = new FuenteRespuestaDto(
                1L, SembradorFuenteDemoServicio.NOMBRE_FUENTE_DEMO, "target-demo", 5432, "ventas_db",
                "demo", true, "public,ventas", List.of("demo"), true, EstadoFuente.FUERA_LINEA,
                null, null, null, null);
        when(fuenteServicio.crear(org.mockito.ArgumentMatchers.any()))
                .thenReturn(creada)
                .thenThrow(new NombreDuplicadoException(SembradorFuenteDemoServicio.NOMBRE_FUENTE_DEMO));

        sembrador.run(null);
        sembrador.run(null);

        verify(fuenteServicio, times(2)).crear(org.mockito.ArgumentMatchers.any());
    }
}
