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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "resultados_chequeos")
public class ResultadoChequeo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analisis_id", nullable = false)
    private Analisis analisis;

    @Column(name = "codigo_chequeo", nullable = false, length = 50)
    private String codigoChequeo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaChequeo categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAnalisis estado;

    @Column(precision = 5, scale = 2)
    private BigDecimal puntaje;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(columnDefinition = "TEXT")
    private String recomendacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> detalle;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Analisis getAnalisis() {
        return analisis;
    }

    public void setAnalisis(Analisis analisis) {
        this.analisis = analisis;
    }

    public String getCodigoChequeo() {
        return codigoChequeo;
    }

    public void setCodigoChequeo(String codigoChequeo) {
        this.codigoChequeo = codigoChequeo;
    }

    public CategoriaChequeo getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaChequeo categoria) {
        this.categoria = categoria;
    }

    public EstadoAnalisis getEstado() {
        return estado;
    }

    public void setEstado(EstadoAnalisis estado) {
        this.estado = estado;
    }

    public BigDecimal getPuntaje() {
        return puntaje;
    }

    public void setPuntaje(BigDecimal puntaje) {
        this.puntaje = puntaje;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getRecomendacion() {
        return recomendacion;
    }

    public void setRecomendacion(String recomendacion) {
        this.recomendacion = recomendacion;
    }

    public Map<String, Object> getDetalle() {
        return detalle == null ? null : Map.copyOf(detalle);
    }

    public void setDetalle(Map<String, Object> detalle) {
        this.detalle = detalle == null ? null : Map.copyOf(detalle);
    }
}
