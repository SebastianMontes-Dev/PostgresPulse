package com.postgrespulse.panel;

import com.postgrespulse.dominio.SslModo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Command object mutable para el formulario HTML "Registrar fuente" del
 * panel de control. Separado de CrearFuenteDto (usado por la API JSON):
 * el binding de formularios de Spring MVC necesita un bean con setters,
 * no un record inmutable.
 */
public class RegistrarFuenteFormulario {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El host es obligatorio")
    private String host;

    @NotNull(message = "El puerto es obligatorio")
    @Min(value = 1, message = "El puerto debe estar entre 1 y 65535")
    @Max(value = 65535, message = "El puerto debe estar entre 1 y 65535")
    private Integer puerto;

    @NotBlank(message = "La base de datos es obligatoria")
    private String baseDeDatos;

    @NotBlank(message = "El usuario es obligatorio")
    private String usuario;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;

    private String filtroEsquema;

    private SslModo sslModo = SslModo.PREFER;

    @Min(value = 0, message = "El umbral de alerta debe estar entre 0 y 100")
    @Max(value = 100, message = "El umbral de alerta debe estar entre 0 y 100")
    private BigDecimal umbralAlerta;

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

    public String getBaseDeDatos() {
        return baseDeDatos;
    }

    public void setBaseDeDatos(String baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getFiltroEsquema() {
        return filtroEsquema;
    }

    public void setFiltroEsquema(String filtroEsquema) {
        this.filtroEsquema = filtroEsquema;
    }

    public SslModo getSslModo() {
        return sslModo;
    }

    public void setSslModo(SslModo sslModo) {
        this.sslModo = sslModo;
    }

    public BigDecimal getUmbralAlerta() {
        return umbralAlerta;
    }

    public void setUmbralAlerta(BigDecimal umbralAlerta) {
        this.umbralAlerta = umbralAlerta;
    }
}
