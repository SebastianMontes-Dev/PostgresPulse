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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServicio(UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioRespuestaDto> listar() {
        return usuarioRepositorio.findAllByOrderByNombreUsuarioAsc().stream()
                .map(UsuarioRespuestaDto::desde)
                .toList();
    }

    @Transactional
    public UsuarioRespuestaDto crear(CrearUsuarioDto dto) {
        if (usuarioRepositorio.existsByNombreUsuarioIgnoreCase(dto.nombreUsuario())) {
            throw new NombreUsuarioDuplicadoException(dto.nombreUsuario());
        }
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(dto.nombreUsuario());
        usuario.setContrasenaHash(passwordEncoder.encode(dto.contrasena()));
        usuario.setRol(dto.rol());
        usuario.setHabilitado(true);
        return UsuarioRespuestaDto.desde(usuarioRepositorio.save(usuario));
    }

    /**
     * Editar rol/habilitado/contrasena de un usuario existente. La proteccion
     * de "ultimo admin" (esUltimoAdminHabilitado) solo aplica si el cambio
     * pedido realmente le quitaria esa condicion (deshabilitarlo o bajarlo de
     * rol) -- editar solo la contrasena de un admin, por ejemplo, nunca la
     * dispara.
     */
    @Transactional
    public UsuarioRespuestaDto editar(Long id, EditarUsuarioDto dto) {
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));

        boolean intentaDeshabilitar = dto.habilitado() != null && !dto.habilitado();
        boolean intentaQuitarRolAdmin = dto.rol() != null && dto.rol() != Rol.ADMIN;
        if ((intentaDeshabilitar || intentaQuitarRolAdmin) && esUltimoAdminHabilitado(usuario)) {
            throw new UltimoAdminException();
        }

        if (dto.contrasena() != null) {
            usuario.setContrasenaHash(passwordEncoder.encode(dto.contrasena()));
        }
        if (dto.rol() != null) {
            usuario.setRol(dto.rol());
        }
        if (dto.habilitado() != null) {
            usuario.setHabilitado(dto.habilitado());
        }
        return UsuarioRespuestaDto.desde(usuarioRepositorio.save(usuario));
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));
        if (esUltimoAdminHabilitado(usuario)) {
            throw new UltimoAdminException();
        }
        usuarioRepositorio.delete(usuario);
    }

    private boolean esUltimoAdminHabilitado(Usuario usuario) {
        return usuario.getRol() == Rol.ADMIN
                && usuario.isHabilitado()
                && usuarioRepositorio.countByRolAndHabilitadoTrue(Rol.ADMIN) <= 1;
    }
}
