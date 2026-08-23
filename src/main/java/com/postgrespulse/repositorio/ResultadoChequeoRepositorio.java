package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.ResultadoChequeo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface ResultadoChequeoRepositorio extends JpaRepository<ResultadoChequeo, Long> {

    List<ResultadoChequeo> findByAnalisisIdOrderByIdAsc(Long analisisId);

    /**
     * Tendencia de un chequeo especifico (panel: selector de chequeo en
     * fuente-detalle). JOIN FETCH evita N+1 al leer analisis.getAnalizadoEn()
     * de cada resultado en el servicio. Los resultados se purgan a los 90
     * dias (ver borrarPorAnalisisAnteriorA) -- el llamador no puede asumir
     * que siempre hay `limite` puntos.
     */
    @Query("SELECT r FROM ResultadoChequeo r JOIN FETCH r.analisis a "
            + "WHERE a.fuente.id = :fuenteId AND r.codigoChequeo = :codigo "
            + "ORDER BY a.analizadoEn DESC")
    List<ResultadoChequeo> buscarTendenciaPorChequeo(Long fuenteId, String codigo, Pageable pageable);

    /**
     * Retencion (docs/SPECS.md #7): borra el detalle granular de resultados
     * de chequeo de analisis con mas de 90 dias; el Analisis en si se
     * conserva, con su detalleJson (totalChequeos/porEstado) como agregado
     * para tendencia historica.
     */
    @Modifying
    @Query("DELETE FROM ResultadoChequeo r WHERE r.analisis.id IN "
            + "(SELECT a.id FROM Analisis a WHERE a.analizadoEn < :corte)")
    int borrarPorAnalisisAnteriorA(OffsetDateTime corte);
}
