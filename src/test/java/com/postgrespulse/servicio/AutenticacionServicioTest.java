package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Rol;
import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.CredencialesInvalidasException;
import com.postgrespulse.repositorio.UsuarioRepositorio;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.JwtServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacionServicioTest {

    private static final String IP = "127.0.0.1";

    @Mock
    private UsuarioRepositorio usuarioRepositorio;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtServicio jwtServicio;
    @Mock
    private ControlIntentosFallidosServicio controlIntentos;

    private AutenticacionServicio servicio;
    private Usuario admin;

    @BeforeEach
    void configurar() {
        servicio = new AutenticacionServicio(usuarioRepositorio, passwordEncoder, jwtServicio, controlIntentos);
        admin = new Usuario();
        admin.setId(1L);
        admin.setNombreUsuario("ana");
        admin.setContrasenaHash("hash");
        admin.setRol(Rol.ADMIN);
        admin.setHabilitado(true);
    }

    @Test
    void loginValidoRegistraExitoYDevuelveElToken() {
        when(usuarioRepositorio.findByNombreUsuarioIgnoreCase("ana")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secreta", "hash")).thenReturn(true);
        when(jwtServicio.generar(admin)).thenReturn("token-jwt");
        OffsetDateTime expiracion = OffsetDateTime.now().plusHours(8);
        when(jwtServicio.validar("token-jwt"))
                .thenReturn(Optional.of(new JwtServicio.ClaimsSesion("ana", Rol.ADMIN, expiracion)));

        TokenRespuestaDto respuesta = servicio.autenticar(new LoginDto("ana", "secreta"), IP);

        assertThat(respuesta.token()).isEqualTo("token-jwt");
        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
        assertThat(respuesta.expiraEn()).isEqualTo(expiracion);
        verify(controlIntentos).registrarExito(IP);
        verify(controlIntentos, never()).registrarFallo(any());
    }

    @Test
    void contrasenaIncorrectaRegistraFalloYLanzaCredencialesInvalidas() {
        when(usuarioRepositorio.findByNombreUsuarioIgnoreCase("ana")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThatThrownBy(() -> servicio.autenticar(new LoginDto("ana", "incorrecta"), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(controlIntentos).registrarFallo(IP);
    }

    @Test
    void usuarioInexistenteRegistraFalloSinTocarBCrypt() {
        when(usuarioRepositorio.findByNombreUsuarioIgnoreCase("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.autenticar(new LoginDto("fantasma", "cualquiera"), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(controlIntentos).registrarFallo(IP);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void usuarioDeshabilitadoNoPuedeAutenticarAunConLaContrasenaCorrecta() {
        admin.setHabilitado(false);
        when(usuarioRepositorio.findByNombreUsuarioIgnoreCase("ana")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> servicio.autenticar(new LoginDto("ana", "secreta"), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(controlIntentos).registrarFallo(IP);
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
