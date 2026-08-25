package com.postgrespulse.excepcion;

import com.postgrespulse.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ManejadorErroresGlobal solo se ejercitaba de rebote a traves de los
 * tests de API (que solo pueden disparar las excepciones que el flujo
 * feliz/de error de esos endpoints efectivamente produce). Este test
 * verifica el mapeo completo excepcion -> codigo HTTP directamente,
 * incluyendo las 2 ramas que ningun otro test toca: NoResourceFoundException
 * (no debe registrarse como error, ver comentario en la clase) y el
 * catch-all de Exception generica -> 500.
 */
@ExtendWith(MockitoExtension.class)
class ManejadorErroresGlobalTest {

    @Mock
    private HttpServletRequest peticion;

    private final ManejadorErroresGlobal manejador = new ManejadorErroresGlobal();

    private void stubRuta(String ruta) {
        when(peticion.getRequestURI()).thenReturn(ruta);
    }

    private void verificar(ResponseEntity<ApiError> respuesta, HttpStatus estadoEsperado, String codigoEsperado) {
        assertThat(respuesta.getStatusCode()).isEqualTo(estadoEsperado);
        ApiError cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.status()).isEqualTo(estadoEsperado.value());
        assertThat(cuerpo.codigo()).isEqualTo(codigoEsperado);
        assertThat(cuerpo.timestamp()).isNotNull();
        assertThat(cuerpo.ruta()).isEqualTo("/api/v1/prueba");
    }

    @Test
    void fuenteNoEncontradaMapeaA404() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.noEncontrada(new FuenteNoEncontradaException(42L), peticion);

        verificar(respuesta, HttpStatus.NOT_FOUND, "NO_ENCONTRADA");
        assertThat(respuesta.getBody().mensaje()).contains("42");
    }

    @Test
    void analisisNoEncontradoMapeaA404() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.analisisNoEncontrado(new AnalisisNoEncontradoException(7L), peticion);

        verificar(respuesta, HttpStatus.NOT_FOUND, "NO_ENCONTRADA");
    }

    @Test
    void nombreDuplicadoMapeaA409() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.nombreDuplicado(new NombreDuplicadoException("Ventas Demo"), peticion);

        verificar(respuesta, HttpStatus.CONFLICT, "CONFLICTO");
    }

    @Test
    void analisisEnCursoMapeaA409() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.analisisEnCurso(new AnalisisEnCursoException(1L), peticion);

        verificar(respuesta, HttpStatus.CONFLICT, "CONFLICTO");
    }

    @Test
    void conexionFallidaMapeaA422() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.conexionFallida(new ConexionFallidaException("timeout"), peticion);

        verificar(respuesta, HttpStatus.UNPROCESSABLE_ENTITY, "CONEXION_FALLIDA");
    }

    @Test
    void extensionAusenteMapeaA400() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.extensionAusente(new ExtensionAusenteException("pg_stat_statements"), peticion);

        verificar(respuesta, HttpStatus.BAD_REQUEST, "EXTENSION_AUSENTE");
    }

    @Test
    void formatoExportacionInvalidoMapeaA400() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.formatoExportacionInvalido(new FormatoExportacionInvalidoException("xml"), peticion);

        verificar(respuesta, HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA");
    }

    @Test
    void recursoEstaticoAusenteMapeaA404SinFiltrarElMensajeOriginal() {
        stubRuta("/favicon.ico");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");

        var respuesta = manejador.recursoNoEncontrado(ex, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // A proposito NO se usa ex.getMessage() (ver comentario en ManejadorErroresGlobal):
        // un mensaje generico evita filtrar rutas internas en la respuesta.
        assertThat(respuesta.getBody().mensaje()).isEqualTo("Recurso no encontrado");
        assertThat(respuesta.getBody().codigo()).isEqualTo("NO_ENCONTRADA");
    }

    @Test
    void validacionMapeaA400ConDetallePorCampo() {
        stubRuta("/api/v1/prueba");
        BindingResult bindingResult = org.mockito.Mockito.mock(BindingResult.class);
        FieldError errorNombre = new FieldError("crearFuenteDto", "nombre", "no debe estar vacío");
        FieldError errorPuerto = new FieldError("crearFuenteDto", "puerto", "debe ser menor o igual a 65535");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(errorNombre, errorPuerto));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                org.mockito.Mockito.mock(MethodParameter.class), bindingResult);

        var respuesta = manejador.validacion(ex, peticion);

        verificar(respuesta, HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA");
        assertThat(respuesta.getBody().detalles())
                .containsExactly("nombre: no debe estar vacío", "puerto: debe ser menor o igual a 65535");
    }

    @Test
    void credencialesInvalidasMapeaA401() {
        stubRuta("/api/v1/auth/login");

        var respuesta = manejador.credencialesInvalidas(new CredencialesInvalidasException(), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody().codigo()).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void demasiadosIntentosMapeaA429ConRetryAfter() {
        stubRuta("/api/v1/auth/login");

        var respuesta = manejador.demasiadosIntentos(new DemasiadosIntentosException(30), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(respuesta.getBody().codigo()).isEqualTo("DEMASIADOS_INTENTOS");
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }

    @Test
    void nombreUsuarioDuplicadoMapeaA409() {
        stubRuta("/api/v1/usuarios");

        var respuesta = manejador.nombreUsuarioDuplicado(new NombreUsuarioDuplicadoException("admin"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().codigo()).isEqualTo("CONFLICTO");
    }

    @Test
    void usuarioNoEncontradoMapeaA404() {
        stubRuta("/api/v1/usuarios/99");

        var respuesta = manejador.usuarioNoEncontrado(new UsuarioNoEncontradoException(99L), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().codigo()).isEqualTo("NO_ENCONTRADA");
    }

    @Test
    void ultimoUsuarioHabilitadoMapeaA409() {
        stubRuta("/api/v1/usuarios/1");

        var respuesta = manejador.ultimoUsuarioHabilitado(new UltimoUsuarioHabilitadoException(), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().codigo()).isEqualTo("CONFLICTO");
    }

    @Test
    void metodoHttpNoSoportadoMapeaA405() {
        // Regresion real: navegar a GET /logout (ruta POST-only) devolvia
        // 500 en vez de 405 antes de agregar este handler.
        stubRuta("/logout");

        var respuesta = manejador.metodoNoSoportado(
                new org.springframework.web.HttpRequestMethodNotSupportedException("GET"), peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody().codigo()).isEqualTo("METODO_NO_SOPORTADO");
    }

    @Test
    void excepcionNoManejadaMapeaA500SinFiltrarDetalleInterno() {
        stubRuta("/api/v1/prueba");

        var respuesta = manejador.errorInesperado(new IllegalStateException("detalle interno sensible"), peticion);

        verificar(respuesta, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO");
        assertThat(respuesta.getBody().mensaje()).isEqualTo("Error interno del servidor");
        assertThat(respuesta.getBody().mensaje()).doesNotContain("detalle interno sensible");
    }
}
