package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.dto.CrearUsuarioDto;
import com.postgrespulse.dto.EditarUsuarioDto;
import com.postgrespulse.dto.UsuarioRespuestaDto;
import com.postgrespulse.excepcion.NombreUsuarioDuplicadoException;
import com.postgrespulse.excepcion.UltimoUsuarioHabilitadoException;
import com.postgrespulse.excepcion.UsuarioNoEncontradoException;
import com.postgrespulse.repositorio.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServicioTest {

    @Mock
    private UsuarioRepositorio usuarioRepositorio;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioServicio servicio;

    @BeforeEach
    void configurar() {
        servicio = new UsuarioServicio(usuarioRepositorio, passwordEncoder);
    }

    private Usuario usuario(Long id, boolean habilitado) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreUsuario("usuario-" + id);
        usuario.setHabilitado(habilitado);
        return usuario;
    }

    @Test
    void creaUnUsuarioConLaContrasenaCifrada() {
        when(usuarioRepositorio.existsByNombreUsuarioIgnoreCase("nuevo")).thenReturn(false);
        when(passwordEncoder.encode("contrasena-fuerte")).thenReturn("hash-bcrypt");
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        UsuarioRespuestaDto respuesta = servicio.crear(new CrearUsuarioDto("nuevo", "contrasena-fuerte"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepositorio).save(captor.capture());
        assertThat(captor.getValue().getContrasenaHash()).isEqualTo("hash-bcrypt");
        assertThat(respuesta.nombreUsuario()).isEqualTo("nuevo");
    }

    @Test
    void rechazaUnNombreDeUsuarioYaExistente() {
        when(usuarioRepositorio.existsByNombreUsuarioIgnoreCase("ana")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioDto("ana", "contrasena-fuerte")))
                .isInstanceOf(NombreUsuarioDuplicadoException.class);

        verify(usuarioRepositorio, never()).save(any());
    }

    @Test
    void editaSoloLaContrasenaSinDispararProteccionDeUltimoUsuario() {
        Usuario usuario = usuario(4L, true);
        when(usuarioRepositorio.findById(4L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("otra-contrasena")).thenReturn("hash-nuevo");
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.editar(4L, new EditarUsuarioDto("otra-contrasena", null));

        assertThat(usuario.getContrasenaHash()).isEqualTo("hash-nuevo");
        assertThat(usuario.isHabilitado()).isTrue();
        // No deberia haber consultado el conteo de habilitados: no se deshabilita a nadie.
        verify(usuarioRepositorio, never()).countByHabilitadoTrue();
    }

    @Test
    void noPermiteDeshabilitarAlUltimoUsuarioHabilitado() {
        Usuario ultimoUsuario = usuario(1L, true);
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(ultimoUsuario));
        when(usuarioRepositorio.countByHabilitadoTrue()).thenReturn(1L);

        assertThatThrownBy(() -> servicio.editar(1L, new EditarUsuarioDto(null, false)))
                .isInstanceOf(UltimoUsuarioHabilitadoException.class);

        verify(usuarioRepositorio, never()).save(any());
    }

    @Test
    void permiteDeshabilitarUnUsuarioSiHayOtrosHabilitados() {
        Usuario usuario = usuario(3L, true);
        when(usuarioRepositorio.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepositorio.countByHabilitadoTrue()).thenReturn(2L);
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioRespuestaDto respuesta = servicio.editar(3L, new EditarUsuarioDto(null, false));

        assertThat(respuesta.habilitado()).isFalse();
    }

    @Test
    void lanzaNoEncontradoAlEditarUnIdInexistente() {
        when(usuarioRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(99L, new EditarUsuarioDto(null, null)))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void eliminaUnUsuarioSiHayOtrosHabilitados() {
        Usuario usuario = usuario(3L, true);
        when(usuarioRepositorio.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepositorio.countByHabilitadoTrue()).thenReturn(2L);

        servicio.eliminar(3L);

        verify(usuarioRepositorio).delete(usuario);
    }

    @Test
    void noPermiteEliminarAlUltimoUsuarioHabilitado() {
        Usuario ultimoUsuario = usuario(1L, true);
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(ultimoUsuario));
        when(usuarioRepositorio.countByHabilitadoTrue()).thenReturn(1L);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(UltimoUsuarioHabilitadoException.class);

        verify(usuarioRepositorio, never()).delete(any());
    }

    @Test
    void lanzaNoEncontradoAlEliminarUnIdInexistente() {
        when(usuarioRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }
}
