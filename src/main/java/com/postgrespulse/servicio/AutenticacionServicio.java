package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.CredencialesInvalidasException;
import com.postgrespulse.repositorio.UsuarioRepositorio;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.JwtServicio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login con usuario/contraseña -> JWT firmado (ROADMAP.md "RBAC + JWT",
 * reemplaza la Autenticación Básica de un solo administrador de v1.0-1.2).
 * El bloqueo por fuerza bruta se evalúa por IP de origen antes de tocar
 * BCrypt (BloqueoPorFuerzaBrutaFilter, delante de /api/v1/auth/login y
 * /login); aquí solo se registra el resultado del intento.
 */
@Service
public class AutenticacionServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JwtServicio jwtServicio;
    private final ControlIntentosFallidosServicio controlIntentos;

    public AutenticacionServicio(UsuarioRepositorio usuarioRepositorio,
                                  PasswordEncoder passwordEncoder,
                                  JwtServicio jwtServicio,
                                  ControlIntentosFallidosServicio controlIntentos) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.jwtServicio = jwtServicio;
        this.controlIntentos = controlIntentos;
    }

    public TokenRespuestaDto autenticar(LoginDto credenciales, String claveBloqueo) {
        Usuario usuario = usuarioRepositorio.findByNombreUsuarioIgnoreCase(credenciales.usuario())
                .filter(Usuario::isHabilitado)
                .orElse(null);

        if (usuario == null || !passwordEncoder.matches(credenciales.contrasena(), usuario.getContrasenaHash())) {
            controlIntentos.registrarFallo(claveBloqueo);
            throw new CredencialesInvalidasException();
        }

        controlIntentos.registrarExito(claveBloqueo);
        String token = jwtServicio.generar(usuario);
        JwtServicio.ClaimsSesion claims = jwtServicio.validar(token).orElseThrow();
        return TokenRespuestaDto.de(token, usuario.getRol(), claims.expiraEn());
    }
}
