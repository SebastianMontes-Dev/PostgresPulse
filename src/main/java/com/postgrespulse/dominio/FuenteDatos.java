package com.postgrespulse.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "fuentes", uniqueConstraints = @UniqueConstraint(columnNames = "nombre"))
public class FuenteDatos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer puerto;

    @Column(name = "nombre_bd", nullable = false, length = 100)
    private String nombreBd;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(name = "contrasena_cifrada", nullable = false, columnDefinition = "TEXT")
    private String contrasenaCifrada;

    @Column(name = "filtro_esquema", length = 200)
    private String filtroEsquema;

    @Column(length = 255)
    private String etiquetas;

    @Column(nullable = false)
    private Boolean habilitado = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFuente estado = EstadoFuente.FUERA_LINEA;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "ultimo_analizado_en")
    private OffsetDateTime ultimoAnalizadoEn;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void prePersist() {
        OffsetDateTime ahora = OffsetDateTime.now();
        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPuerto() {
        return puerto;
    }

    public void setPuerto(Integer puerto) {
        this.puerto = puerto;
    }

    public String getNombreBd() {
        return nombreBd;
    }

    public void setNombreBd(String nombreBd) {
        this.nombreBd = nombreBd;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContrasenaCifrada() {
        return contrasenaCifrada;
    }

    public void setContrasenaCifrada(String contrasenaCifrada) {
        this.contrasenaCifrada = contrasenaCifrada;
    }

    public String getFiltroEsquema() {
        return filtroEsquema;
    }

    public void setFiltroEsquema(String filtroEsquema) {
        this.filtroEsquema = filtroEsquema;
    }

    public String getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(String etiquetas) {
        this.etiquetas = etiquetas;
    }

    public Boolean getHabilitado() {
        return habilitado;
    }

    public void setHabilitado(Boolean habilitado) {
        this.habilitado = habilitado;
    }

    public EstadoFuente getEstado() {
        return estado;
    }

    public void setEstado(EstadoFuente estado) {
        this.estado = estado;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public OffsetDateTime getUltimoAnalizadoEn() {
        return ultimoAnalizadoEn;
    }

    public void setUltimoAnalizadoEn(OffsetDateTime ultimoAnalizadoEn) {
        this.ultimoAnalizadoEn = ultimoAnalizadoEn;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
