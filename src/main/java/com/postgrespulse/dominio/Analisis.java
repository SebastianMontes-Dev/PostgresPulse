package com.postgrespulse.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "analisis")
public class Analisis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuente_id", nullable = false)
    private FuenteDatos fuente;

    @Column(name = "puntaje_salud", precision = 5, scale = 2)
    private BigDecimal puntajeSalud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAnalisis estado;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "analizado_en", nullable = false)
    private OffsetDateTime analizadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "disparado_por", nullable = false, length = 20)
    private TipoDisparo disparadoPor = TipoDisparo.MANUAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle_json")
    private Map<String, Object> detalleJson;

    @PrePersist
    void prePersist() {
        if (analizadoEn == null) {
            analizadoEn = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FuenteDatos getFuente() {
        return fuente;
    }

    public void setFuente(FuenteDatos fuente) {
        this.fuente = fuente;
    }

    public BigDecimal getPuntajeSalud() {
        return puntajeSalud;
    }

    public void setPuntajeSalud(BigDecimal puntajeSalud) {
        this.puntajeSalud = puntajeSalud;
    }

    public EstadoAnalisis getEstado() {
        return estado;
    }

    public void setEstado(EstadoAnalisis estado) {
        this.estado = estado;
    }

    public Long getDuracionMs() {
        return duracionMs;
    }

    public void setDuracionMs(Long duracionMs) {
        this.duracionMs = duracionMs;
    }

    public OffsetDateTime getAnalizadoEn() {
        return analizadoEn;
    }

    public void setAnalizadoEn(OffsetDateTime analizadoEn) {
        this.analizadoEn = analizadoEn;
    }

    public TipoDisparo getDisparadoPor() {
        return disparadoPor;
    }

    public void setDisparadoPor(TipoDisparo disparadoPor) {
        this.disparadoPor = disparadoPor;
    }

    public Map<String, Object> getDetalleJson() {
        return detalleJson == null ? null : Map.copyOf(detalleJson);
    }

    public void setDetalleJson(Map<String, Object> detalleJson) {
        this.detalleJson = detalleJson == null ? null : Map.copyOf(detalleJson);
    }
}
