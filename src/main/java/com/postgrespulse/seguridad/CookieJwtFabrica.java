package com.postgrespulse.seguridad;

import com.postgrespulse.config.PropiedadesJwt;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Construye la cookie httpOnly que porta el JWT para el panel (ver
 * JwtAuthenticationFilter). SameSite=Strict porque el panel no necesita
 * navegación cross-site autenticada; `Secure` queda condicionado a que la
 * app corra detrás de TLS (docs/DEPLOYMENT.md #4.6) -- en el demo local por
 * HTTP activarlo rompería el login, así que se decide en tiempo de arranque
 * según `server.ssl.enabled`, no hardcodeado.
 */
@Component
public class CookieJwtFabrica {

    private final long expiracionSegundos;
    private final boolean secure;

    public CookieJwtFabrica(PropiedadesJwt propiedadesJwt,
                             org.springframework.core.env.Environment environment) {
        this.expiracionSegundos = propiedadesJwt.getExpiracionMinutos() * 60;
        this.secure = environment.getProperty("server.ssl.enabled", Boolean.class, false);
    }

    public ResponseCookie crear(String token) {
        return base().value(token).maxAge(expiracionSegundos).build();
    }

    public ResponseCookie limpiar() {
        return base().value("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base() {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NOMBRE)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
