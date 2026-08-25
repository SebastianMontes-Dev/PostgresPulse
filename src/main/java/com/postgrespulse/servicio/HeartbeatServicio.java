package com.postgrespulse.servicio;

import com.postgrespulse.config.PropiedadesHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * "Dead man's switch": hace ping periodico a una URL externa configurada por
 * el operador (un servicio tipo healthchecks.io/cronitor/Uptime Kuma, no
 * elegido aqui a proposito -- cualquiera que acepte un GET periodico sirve).
 * Si PostgresPulse se cae o queda en crashloop, el ping deja de llegar y es
 * ese servicio externo el que dispara la alerta -- los canales de
 * AlertaServicio no sirven para esto porque dependen de que la propia app
 * este viva para dispararlos. Sin app.heartbeat.url configurada (vacia por
 * defecto), no hace nada.
 */
@Service
public class HeartbeatServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(HeartbeatServicio.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final PropiedadesHeartbeat propiedades;
    private final RestClient restClient;

    @Autowired
    public HeartbeatServicio(PropiedadesHeartbeat propiedades) {
        this(propiedades, RestClient.builder().requestFactory(fabricaConTimeout()));
    }

    HeartbeatServicio(PropiedadesHeartbeat propiedades, RestClient.Builder restClientBuilder) {
        this.propiedades = propiedades;
        this.restClient = restClientBuilder.build();
    }

    @Scheduled(fixedDelayString = "#{${app.heartbeat.intervalo-minutos:5} * 60000}")
    public void enviarPing() {
        String url = propiedades.getUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            REGISTRO.warn("No se pudo enviar el heartbeat a {}", url, ex);
        }
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeout() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) TIMEOUT.toMillis());
        fabrica.setReadTimeout((int) TIMEOUT.toMillis());
        return fabrica;
    }
}
