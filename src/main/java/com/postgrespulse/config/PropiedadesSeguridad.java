package com.postgrespulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.seguridad")
public class PropiedadesSeguridad {

    private String usuario;
    private String contrasena;
    private boolean bloquearDefaultsInseguros;
    private List<String> proxiesConfiables = List.of();

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

    public boolean isBloquearDefaultsInseguros() {
        return bloquearDefaultsInseguros;
    }

    public void setBloquearDefaultsInseguros(boolean bloquearDefaultsInseguros) {
        this.bloquearDefaultsInseguros = bloquearDefaultsInseguros;
    }

    public List<String> getProxiesConfiables() {
        return List.copyOf(proxiesConfiables);
    }

    public void setProxiesConfiables(List<String> proxiesConfiables) {
        this.proxiesConfiables = proxiesConfiables == null ? List.of() : List.copyOf(proxiesConfiables);
    }
}
