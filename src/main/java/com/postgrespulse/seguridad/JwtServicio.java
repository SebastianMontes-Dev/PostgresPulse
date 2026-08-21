package com.postgrespulse.seguridad;

import com.postgrespulse.config.PropiedadesJwt;
import com.postgrespulse.dominio.Rol;
import com.postgrespulse.dominio.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

/**
 * Firma y valida los JWT de sesion (HS256). La clave HMAC se deriva con
 * SHA-256 de PULSE_JWT_SECRET -- mismo criterio que CifradoServicio con
 * PULSE_CRYPTO_KEY -- para no exigir exactamente 32 bytes crudos y evitar
 * WeakKeyException con un secreto corto pero razonable.
 */
@Service
public final class JwtServicio {

    private final SecretKey clave;
    private final long expiracionMinutos;

    public JwtServicio(PropiedadesJwt propiedades) {
        String secreto = propiedades.getSecreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("PULSE_JWT_SECRET debe tener al menos 16 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(derivarClave256(secreto));
        this.expiracionMinutos = propiedades.getExpiracionMinutos();
    }

    public String generar(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plusSeconds(expiracionMinutos * 60);
        return Jwts.builder()
                .subject(usuario.getNombreUsuario())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(clave)
                .compact();
    }

    /** Vacío si el token es inválido, está expirado o fue alterado; nunca lanza hacia el llamador. */
    public Optional<ClaimsSesion> validar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            Rol rol = Rol.valueOf(claims.get("rol", String.class));
            OffsetDateTime expiraEn = claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC);
            return Optional.of(new ClaimsSesion(claims.getSubject(), rol, expiraEn));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record ClaimsSesion(String usuario, Rol rol, OffsetDateTime expiraEn) {
    }

    private static byte[] derivarClave256(String secreto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo derivar la clave de firma JWT", ex);
        }
    }
}
