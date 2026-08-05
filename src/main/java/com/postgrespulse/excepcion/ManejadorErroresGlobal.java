package com.postgrespulse.excepcion;

import com.postgrespulse.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ManejadorErroresGlobal {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ManejadorErroresGlobal.class);

    @ExceptionHandler(FuenteNoEncontradaException.class)
    public ResponseEntity<ApiError> noEncontrada(FuenteNoEncontradaException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    @ExceptionHandler(NombreDuplicadoException.class)
    public ResponseEntity<ApiError> nombreDuplicado(NombreDuplicadoException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.CONFLICT, "CONFLICTO", peticion, List.of());
    }

    @ExceptionHandler(ConexionFallidaException.class)
    public ResponseEntity<ApiError> conexionFallida(ConexionFallidaException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY, "CONEXION_FALLIDA", peticion, List.of());
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
