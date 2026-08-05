package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.FuenteDatos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FuenteDatosRepositorio extends JpaRepository<FuenteDatos, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<FuenteDatos> findByNombreIgnoreCase(String nombre);

    List<FuenteDatos> findByHabilitadoTrueOrderByNombreAsc();
}
