package com.postgrespulse.controlador;

import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.DemasiadosIntentosException;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.CookieJwtFabrica;
import com.postgrespulse.servicio.AutenticacionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Login para clientes de la API (`Authorization: Bearer` en cada petición
 * posterior). El panel Thymeleaf usa su propio formulario en PanelControlador
 * (misma lógica vía AutenticacionServicio, pero sirve HTML y redirige en vez
 * de JSON) -- ver JwtAuthenticationFilter para por qué la cookie que esta
 * respuesta también fija no le sirve de nada a un cliente de /api/v1/**.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthControlador {

    private final AutenticacionServicio autenticacionServicio;
    private final ControlIntentosFallidosServicio controlIntentos;
    private final CookieJwtFabrica cookieJwtFabrica;

    public AuthControlador(AutenticacionServicio autenticacionServicio,
                            ControlIntentosFallidosServicio controlIntentos,
                            CookieJwtFabrica cookieJwtFabrica) {
        this.autenticacionServicio = autenticacionServicio;
        this.controlIntentos = controlIntentos;
        this.cookieJwtFabrica = cookieJwtFabrica;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenRespuestaDto> login(@Valid @RequestBody LoginDto credenciales, HttpServletRequest peticion) {
        String ip = peticion.getRemoteAddr();
        Duration restante = controlIntentos.tiempoRestanteBloqueo(ip);
        if (!restante.isZero()) {
            throw new DemasiadosIntentosException(Math.max(1, restante.toSeconds()));
        }

        TokenRespuestaDto respuesta = autenticacionServicio.autenticar(credenciales, ip);
        ResponseCookie cookie = cookieJwtFabrica.crear(respuesta.token());
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(respuesta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = cookieJwtFabrica.limpiar();
        return ResponseEntity.noContent().header("Set-Cookie", cookie.toString()).build();
    }
}
