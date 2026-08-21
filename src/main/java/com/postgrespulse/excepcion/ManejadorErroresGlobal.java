package com.postgrespulse.excepcion;

import com.postgrespulse.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ManejadorErroresGlobal {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ManejadorErroresGlobal.class);

    @ExceptionHandler(FuenteNoEncontradaException.class)
    public ResponseEntity<ApiError> noEncontrada(FuenteNoEncontradaException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    @ExceptionHandler(AnalisisNoEncontradoException.class)
    public ResponseEntity<ApiError> analisisNoEncontrado(AnalisisNoEncontradoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    @ExceptionHandler(NombreDuplicadoException.class)
    public ResponseEntity<ApiError> nombreDuplicado(NombreDuplicadoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.CONFLICT, "CONFLICTO", peticion, List.of());
    }

    @ExceptionHandler(NombreUsuarioDuplicadoException.class)
    public ResponseEntity<ApiError> nombreUsuarioDuplicado(NombreUsuarioDuplicadoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.CONFLICT, "CONFLICTO", peticion, List.of());
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<ApiError> usuarioNoEncontrado(UsuarioNoEncontradoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    @ExceptionHandler(UltimoAdminException.class)
    public ResponseEntity<ApiError> ultimoAdmin(UltimoAdminException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.CONFLICT, "CONFLICTO", peticion, List.of());
    }

    /**
     * @PreAuthorize deniega lanzando AuthorizationDeniedException (Spring
     * Security 6), que extiende esta clase para compatibilidad. Sin este
     * handler explicito, la excepcion la atrapa errorInesperado() de mas
     * abajo -- @RestControllerAdvice resuelve excepciones DENTRO del
     * DispatcherServlet, antes de que le llegue a ExceptionTranslationFilter
     * (el traductor a 403 de Spring Security, que vive fuera, a nivel de
     * filtro) -- y un rechazo de autorizacion real terminaba devolviendo 500
     * en vez de 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accesoDenegado(AccessDeniedException ex, HttpServletRequest peticion) {
        return construir("No tiene permisos para realizar esta acción", HttpStatus.FORBIDDEN,
                "ACCESO_DENEGADO", peticion, List.of());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> credencialesInvalidas(CredencialesInvalidasException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", peticion, List.of());
    }

    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ApiError> demasiadosIntentos(DemasiadosIntentosException ex, HttpServletRequest peticion) {
        ResponseEntity<ApiError> respuesta = construir(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS,
                "DEMASIADOS_INTENTOS", peticion, List.of());
        return ResponseEntity.status(respuesta.getStatusCode())
                .header("Retry-After", String.valueOf(ex.getSegundosRestantes()))
                .body(respuesta.getBody());
    }

    @ExceptionHandler(AnalisisEnCursoException.class)
    public ResponseEntity<ApiError> analisisEnCurso(AnalisisEnCursoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.CONFLICT, "CONFLICTO", peticion, List.of());
    }

    @ExceptionHandler(ConexionFallidaException.class)
    public ResponseEntity<ApiError> conexionFallida(ConexionFallidaException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY, "CONEXION_FALLIDA", peticion, List.of());
    }

    @ExceptionHandler(ExtensionAusenteException.class)
    public ResponseEntity<ApiError> extensionAusente(ExtensionAusenteException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.BAD_REQUEST, "EXTENSION_AUSENTE", peticion, List.of());
    }

    @ExceptionHandler(FormatoExportacionInvalidoException.class)
    public ResponseEntity<ApiError> formatoExportacionInvalido(FormatoExportacionInvalidoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", peticion, List.of());
    }

    /**
     * Recurso estatico ausente (ej. favicon.ico que el navegador pide solo):
     * no es un fallo del servidor, no debe caer en errorInesperado() ni
     * registrarse en ERROR -- eso llenaria los logs de produccion con
     * "500" falsos por cada 404 legitimo de un asset inexistente.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> recursoNoEncontrado(NoResourceFoundException ex, HttpServletRequest peticion) {
        return construir("Recurso no encontrado", HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    /**
     * Encontrado navegando manualmente a GET /logout (ruta POST-only): sin
     * este handler, HttpRequestMethodNotSupportedException caia igual que
     * AccessDeniedException en errorInesperado() -> 500 en vez de 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodoNoSoportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_SOPORTADO", peticion, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest peticion) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return construir("Validación de entrada fallida", HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", peticion, detalles);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> errorInesperado(Exception ex, HttpServletRequest peticion) {
        REGISTRO.error("Error interno no manejado", ex);
        return construir("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", peticion, List.of());
    }

    private ResponseEntity<ApiError> construir(String mensaje, HttpStatus estado, String codigo,
                                               HttpServletRequest peticion, List<String> detalles) {
        ApiError error = new ApiError(OffsetDateTime.now(), estado.value(), codigo, mensaje, peticion.getRequestURI(), detalles);
        return ResponseEntity.status(estado).body(error);
    }
}
