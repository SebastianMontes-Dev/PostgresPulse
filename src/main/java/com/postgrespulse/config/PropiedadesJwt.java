package com.postgrespulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class PropiedadesJwt {

    private String secreto;
    private long expiracionMinutos = 480;

    public String getSecreto() {
        return secreto;
    }

    public void setSecreto(String secreto) {
        this.secreto = secreto;
    }

    public long getExpiracionMinutos() {
        return expiracionMinutos;
    }

    public void setExpiracionMinutos(long expiracionMinutos) {
        this.expiracionMinutos = expiracionMinutos;
    }
}
