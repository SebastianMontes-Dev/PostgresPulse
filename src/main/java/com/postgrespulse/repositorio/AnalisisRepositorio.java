package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.Analisis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalisisRepositorio extends JpaRepository<Analisis, Long> {

    Page<Analisis> findByFuenteIdOrderByAnalizadoEnDesc(Long fuenteId, Pageable pageable);

    List<Analisis> findTop7ByFuenteIdOrderByAnalizadoEnDesc(Long fuenteId);
}
