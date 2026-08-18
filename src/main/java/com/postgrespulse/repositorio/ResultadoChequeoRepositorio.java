package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.ResultadoChequeo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface ResultadoChequeoRepositorio extends JpaRepository<ResultadoChequeo, Long> {

    List<ResultadoChequeo> findByAnalisisIdOrderByIdAsc(Long analisisId);

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
