package com.postgrespulse.servicio;

import com.postgrespulse.conexion.RegistroConexionesServicio;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dto.ActualizarFuenteDto;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.excepcion.NombreDuplicadoException;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.servicio.cifrado.CifradoServicio;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class FuenteServicio {

    private final FuenteDatosRepositorio repositorio;
    private final CifradoServicio cifradoServicio;
    private final RegistroConexionesServicio registroConexiones;

    public FuenteServicio(FuenteDatosRepositorio repositorio,
                          CifradoServicio cifradoServicio,
                          RegistroConexionesServicio registroConexiones) {
        this.repositorio = repositorio;
        this.cifradoServicio = cifradoServicio;
        this.registroConexiones = registroConexiones;
    }

    public List<FuenteRespuestaDto> listar() {
        return repositorio.findAll(Sort.by("nombre")).stream().map(this::aRespuesta).toList();
    }

    public FuenteRespuestaDto obtener(Long id) {
        return aRespuesta(buscar(id));
    }

    @Transactional
    public FuenteRespuestaDto crear(CrearFuenteDto dto) {
        if (repositorio.existsByNombreIgnoreCase(dto.nombre())) {
            throw new NombreDuplicadoException(dto.nombre());
        }
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre(dto.nombre());
        fuente.setHost(dto.host());
        fuente.setPuerto(dto.puerto());
        fuente.setNombreBd(dto.baseDeDatos());
        fuente.setUsuario(dto.usuario());
        fuente.setContrasenaCifrada(cifradoServicio.cifrar(dto.contrasena()));
        fuente.setFiltroEsquema(dto.filtroEsquema());
        fuente.setEtiquetas(unirEtiquetas(dto.etiquetas()));
        fuente.setHabilitado(Boolean.TRUE);
        fuente.setSslModo(dto.sslModo());
        return aRespuesta(repositorio.save(fuente));
    }

    @Transactional
    public FuenteRespuestaDto actualizar(Long id, ActualizarFuenteDto dto) {
        FuenteDatos fuente = buscar(id);
        if (dto.nombre() != null) {
            if (!dto.nombre().equalsIgnoreCase(fuente.getNombre()) && repositorio.existsByNombreIgnoreCase(dto.nombre())) {
                throw new NombreDuplicadoException(dto.nombre());
            }
            fuente.setNombre(dto.nombre());
        }
        if (dto.host() != null) {
            fuente.setHost(dto.host());
        }
        if (dto.puerto() != null) {
            fuente.setPuerto(dto.puerto());
        }
        if (dto.baseDeDatos() != null) {
            fuente.setNombreBd(dto.baseDeDatos());
        }
        if (dto.usuario() != null) {
            fuente.setUsuario(dto.usuario());
        }
        if (dto.contrasena() != null) {
            fuente.setContrasenaCifrada(cifradoServicio.cifrar(dto.contrasena()));
        }
        if (dto.filtroEsquema() != null) {
            fuente.setFiltroEsquema(dto.filtroEsquema());
        }
        if (dto.etiquetas() != null) {
            fuente.setEtiquetas(unirEtiquetas(dto.etiquetas()));
        }
        if (dto.habilitado() != null) {
            fuente.setHabilitado(dto.habilitado());
        }
        if (dto.sslModo() != null) {
            fuente.setSslModo(dto.sslModo());
        }
        FuenteDatos guardada = repositorio.save(fuente);
        registroConexiones.eliminar(id);
        return aRespuesta(guardada);
    }

    @Transactional
    public void eliminar(Long id) {
        buscar(id);
        repositorio.deleteById(id);
        registroConexiones.eliminar(id);
    }

    private FuenteDatos buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new FuenteNoEncontradaException(id));
    }

    private FuenteRespuestaDto aRespuesta(FuenteDatos fuente) {
        return new FuenteRespuestaDto(
                fuente.getId(),
                fuente.getNombre(),
                fuente.getHost(),
                fuente.getPuerto(),
                fuente.getNombreBd(),
                fuente.getUsuario(),
                true,
                fuente.getFiltroEsquema(),
                separarEtiquetas(fuente.getEtiquetas()),
                Boolean.TRUE.equals(fuente.getHabilitado()),
                fuente.getEstado(),
                fuente.getSslModo(),
                fuente.getUltimoError(),
                fuente.getUltimoAnalizadoEn(),
                fuente.getCreadoEn(),
                fuente.getActualizadoEn());
    }

    private String unirEtiquetas(List<String> etiquetas) {
        if (etiquetas == null || etiquetas.isEmpty()) {
            return null;
        }
        return String.join(",", etiquetas);
    }

    private List<String> separarEtiquetas(String etiquetas) {
        if (etiquetas == null || etiquetas.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(etiquetas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
