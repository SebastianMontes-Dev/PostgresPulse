package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.dto.CrearUsuarioDto;
import com.postgrespulse.dto.EditarUsuarioDto;
import com.postgrespulse.dto.UsuarioRespuestaDto;
import com.postgrespulse.excepcion.NombreUsuarioDuplicadoException;
import com.postgrespulse.excepcion.UltimoUsuarioHabilitadoException;
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
        usuario.setHabilitado(true);
        return UsuarioRespuestaDto.desde(usuarioRepositorio.save(usuario));
    }

    /**
     * Editar habilitado/contrasena de un usuario existente. La proteccion de
     * "ultimo usuario habilitado" (esUltimoUsuarioHabilitado) solo aplica si
     * el cambio pedido realmente deshabilitaria al unico usuario que puede
     * entrar -- editar solo la contrasena nunca la dispara.
     */
    @Transactional
    public UsuarioRespuestaDto editar(Long id, EditarUsuarioDto dto) {
        Usuario usuario = usuarioRepositorio.findById(id)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id));

        boolean intentaDeshabilitar = dto.habilitado() != null && !dto.habilitado();
        if (intentaDeshabilitar && esUltimoUsuarioHabilitado(usuario)) {
            throw new UltimoUsuarioHabilitadoException();
        }

        if (dto.contrasena() != null) {
            usuario.setContrasenaHash(passwordEncoder.encode(dto.contrasena()));
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
        if (esUltimoUsuarioHabilitado(usuario)) {
            throw new UltimoUsuarioHabilitadoException();
        }
        usuarioRepositorio.delete(usuario);
    }

    private boolean esUltimoUsuarioHabilitado(Usuario usuario) {
        return usuario.isHabilitado() && usuarioRepositorio.countByHabilitadoTrue() <= 1;
    }
}
