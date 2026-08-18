package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.Analisis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalisisRepositorio extends JpaRepository<Analisis, Long> {

    Page<Analisis> findByFuenteIdOrderByAnalizadoEnDesc(Long fuenteId, Pageable pageable);

    List<Analisis> findTop7ByFuenteIdOrderByAnalizadoEnDesc(Long fuenteId);

    Optional<Analisis> findFirstByFuenteIdOrderByAnalizadoEnDesc(Long fuenteId);

    /** Fetch-join de la fuente: evita LazyInitializationException con open-in-view=false. */
    @Query("SELECT a FROM Analisis a JOIN FETCH a.fuente WHERE a.id = :id")
    Optional<Analisis> buscarConFuente(Long id);
}
