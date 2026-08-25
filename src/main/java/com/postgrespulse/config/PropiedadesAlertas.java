package com.postgrespulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alertas")
public class PropiedadesAlertas {

    private boolean emailHabilitado;
    private String emailDesde;
    private String emailPara;
    private String slackWebhookUrl;
    private String pagerdutyRoutingKey;

    public boolean isEmailHabilitado() {
        return emailHabilitado;
    }

    public void setEmailHabilitado(boolean emailHabilitado) {
        this.emailHabilitado = emailHabilitado;
    }

    public String getEmailDesde() {
        return emailDesde;
    }

    public void setEmailDesde(String emailDesde) {
        this.emailDesde = emailDesde;
    }

    public String getEmailPara() {
        return emailPara;
    }

    public void setEmailPara(String emailPara) {
        this.emailPara = emailPara;
    }

    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }

    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }

    public String getPagerdutyRoutingKey() {
        return pagerdutyRoutingKey;
    }

    public void setPagerdutyRoutingKey(String pagerdutyRoutingKey) {
        this.pagerdutyRoutingKey = pagerdutyRoutingKey;
    }
}
