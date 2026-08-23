package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Rol;
import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.dto.CrearUsuarioDto;
import com.postgrespulse.dto.EditarUsuarioDto;
import com.postgrespulse.dto.UsuarioRespuestaDto;
import com.postgrespulse.excepcion.NombreUsuarioDuplicadoException;
import com.postgrespulse.excepcion.UltimoAdminException;
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

    private Usuario usuario(Long id, Rol rol, boolean habilitado) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreUsuario("usuario-" + id);
        usuario.setRol(rol);
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

        UsuarioRespuestaDto respuesta = servicio.crear(new CrearUsuarioDto("nuevo", "contrasena-fuerte", Rol.LECTOR));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepositorio).save(captor.capture());
        assertThat(captor.getValue().getContrasenaHash()).isEqualTo("hash-bcrypt");
        assertThat(respuesta.nombreUsuario()).isEqualTo("nuevo");
        assertThat(respuesta.rol()).isEqualTo(Rol.LECTOR);
    }

    @Test
    void rechazaUnNombreDeUsuarioYaExistente() {
        when(usuarioRepositorio.existsByNombreUsuarioIgnoreCase("ana")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioDto("ana", "contrasena-fuerte", Rol.ADMIN)))
                .isInstanceOf(NombreUsuarioDuplicadoException.class);

        verify(usuarioRepositorio, never()).save(any());
    }

    @Test
    void editaSoloLaContrasenaDeUnAdminSinDispararProteccionDeUltimoAdmin() {
        Usuario admin = usuario(4L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(4L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("otra-contrasena")).thenReturn("hash-nuevo");
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.editar(4L, new EditarUsuarioDto("otra-contrasena", null, null));

        assertThat(admin.getContrasenaHash()).isEqualTo("hash-nuevo");
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isHabilitado()).isTrue();
        // No deberia haber consultado el conteo de admins: ni deshabilita ni cambia de rol.
        verify(usuarioRepositorio, never()).countByRolAndHabilitadoTrue(any());
    }

    @Test
    void editaRolYHabilitadoDeUnLectorSinRestriccion() {
        Usuario lector = usuario(5L, Rol.LECTOR, true);
        when(usuarioRepositorio.findById(5L)).thenReturn(Optional.of(lector));
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioRespuestaDto respuesta = servicio.editar(5L, new EditarUsuarioDto(null, Rol.ADMIN, false));

        assertThat(respuesta.rol()).isEqualTo(Rol.ADMIN);
        assertThat(respuesta.habilitado()).isFalse();
    }

    @Test
    void noPermiteDeshabilitarAlUltimoAdminHabilitado() {
        Usuario ultimoAdmin = usuario(1L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(ultimoAdmin));
        when(usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.editar(1L, new EditarUsuarioDto(null, null, false)))
                .isInstanceOf(UltimoAdminException.class);

        verify(usuarioRepositorio, never()).save(any());
    }

    @Test
    void noPermiteBajarleElRolAlUltimoAdminHabilitado() {
        Usuario ultimoAdmin = usuario(1L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(ultimoAdmin));
        when(usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.editar(1L, new EditarUsuarioDto(null, Rol.LECTOR, null)))
                .isInstanceOf(UltimoAdminException.class);

        verify(usuarioRepositorio, never()).save(any());
    }

    @Test
    void permiteDeshabilitarUnAdminSiHayOtrosAdminsHabilitados() {
        Usuario admin = usuario(3L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(3L)).thenReturn(Optional.of(admin));
        when(usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN)).thenReturn(2L);
        when(usuarioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsuarioRespuestaDto respuesta = servicio.editar(3L, new EditarUsuarioDto(null, null, false));

        assertThat(respuesta.habilitado()).isFalse();
    }

    @Test
    void lanzaNoEncontradoAlEditarUnIdInexistente() {
        when(usuarioRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(99L, new EditarUsuarioDto(null, Rol.ADMIN, null)))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void eliminaUnUsuarioLector() {
        Usuario lector = usuario(2L, Rol.LECTOR, true);
        when(usuarioRepositorio.findById(2L)).thenReturn(Optional.of(lector));

        servicio.eliminar(2L);

        verify(usuarioRepositorio).delete(lector);
    }

    @Test
    void eliminaUnAdminSiHayOtrosAdminsHabilitados() {
        Usuario admin = usuario(3L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(3L)).thenReturn(Optional.of(admin));
        when(usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN)).thenReturn(2L);

        servicio.eliminar(3L);

        verify(usuarioRepositorio).delete(admin);
    }

    @Test
    void noPermiteEliminarAlUltimoAdminHabilitado() {
        Usuario ultimoAdmin = usuario(1L, Rol.ADMIN, true);
        when(usuarioRepositorio.findById(1L)).thenReturn(Optional.of(ultimoAdmin));
        when(usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(UltimoAdminException.class);

        verify(usuarioRepositorio, never()).delete(any());
    }

    @Test
    void lanzaNoEncontradoAlEliminarUnIdInexistente() {
        when(usuarioRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }
}
