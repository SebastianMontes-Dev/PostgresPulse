package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.ResultadoChequeo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoChequeoRepositorio extends JpaRepository<ResultadoChequeo, Long> {

    List<ResultadoChequeo> findByAnalisisIdOrderByIdAsc(Long analisisId);
}
