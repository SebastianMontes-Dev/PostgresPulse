package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.Rol;
import com.postgrespulse.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNombreUsuarioIgnoreCase(String nombreUsuario);

    boolean existsByNombreUsuarioIgnoreCase(String nombreUsuario);

    long countByRolAndHabilitadoTrue(Rol rol);

    List<Usuario> findAllByOrderByNombreUsuarioAsc();
}
