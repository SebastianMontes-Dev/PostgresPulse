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

    /**
     * Un solo round-trip para el ultimo analisis de cada fuente, en vez de
     * llamar findFirstByFuenteIdOrderByAnalizadoEnDesc en un loop (N+1 que
     * tenia el panel de control en la pantalla Resumen). MAX(id) equivale a
     * "el mas reciente" porque los ids son BIGSERIAL insertados en orden
     * cronologico y las filas nunca se reordenan.
     */
    @Query("SELECT a FROM Analisis a WHERE a.id IN "
            + "(SELECT MAX(a2.id) FROM Analisis a2 WHERE a2.fuente.id IN :fuenteIds GROUP BY a2.fuente.id)")
    List<Analisis> buscarUltimosPorFuentes(List<Long> fuenteIds);
}
