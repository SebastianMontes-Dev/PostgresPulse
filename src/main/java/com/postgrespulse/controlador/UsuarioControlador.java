package com.postgrespulse.controlador;

import com.postgrespulse.dto.CrearUsuarioDto;
import com.postgrespulse.dto.EditarUsuarioDto;
import com.postgrespulse.dto.UsuarioRespuestaDto;
import com.postgrespulse.servicio.UsuarioServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestión de usuarios: cualquier cuenta autenticada puede administrar cuentas. */
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioControlador {

    private final UsuarioServicio usuarioServicio;

    public UsuarioControlador(UsuarioServicio usuarioServicio) {
        this.usuarioServicio = usuarioServicio;
    }

    @GetMapping
    public List<UsuarioRespuestaDto> listar() {
        return usuarioServicio.listar();
    }

    @PostMapping
    public ResponseEntity<UsuarioRespuestaDto> crear(@Valid @RequestBody CrearUsuarioDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioServicio.crear(dto));
    }

    @PutMapping("/{id}")
    public UsuarioRespuestaDto editar(@PathVariable Long id, @Valid @RequestBody EditarUsuarioDto dto) {
        return usuarioServicio.editar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        usuarioServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
