package com.postgrespulse.servicio;

import com.postgrespulse.config.PropiedadesAlertas;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Evalua si un analisis nuevo cruzo el umbral de alerta configurado por
 * fuente (FuenteDatos.umbralAlerta) y, si hubo cruce, despacha una
 * notificacion a los canales habilitados globalmente para la instancia
 * (PropiedadesAlertas: email/Slack/PagerDuty). Sin umbral configurado para
 * la fuente, no hace nada -- el umbral es opt-in por fuente, los canales de
 * envio son configuracion de instancia, no de fuente.
 *
 * Cada canal se despacha en su propio try/catch: un canal caido (SMTP sin
 * responder, webhook con timeout) no debe tumbar a los otros canales ni,
 * mas importante, al ciclo de analisis que disparo la evaluacion.
 */
@Service
public class AlertaServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(AlertaServicio.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String URL_PAGERDUTY = "https://events.pagerduty.com/v2/enqueue";

    private final PropiedadesAlertas propiedades;
    private final JavaMailSender javaMailSender;
    private final RestClient restClient;

    @Autowired
    public AlertaServicio(PropiedadesAlertas propiedades, JavaMailSender javaMailSender) {
        this(propiedades, javaMailSender, RestClient.builder().requestFactory(fabricaConTimeout()));
    }

    AlertaServicio(PropiedadesAlertas propiedades, JavaMailSender javaMailSender, RestClient.Builder restClientBuilder) {
        this.propiedades = propiedades;
        this.javaMailSender = javaMailSender;
        this.restClient = restClientBuilder.build();
    }

    public void evaluar(FuenteDatos fuente, Analisis anterior, Analisis nuevo) {
        BigDecimal umbral = fuente.getUmbralAlerta();
        if (umbral == null) {
            return;
        }
        BigDecimal puntajeAnterior = anterior == null ? null : anterior.getPuntajeSalud();
        BigDecimal puntajeNuevo = nuevo.getPuntajeSalud();
        if (puntajeAnterior == null || puntajeNuevo == null) {
            return;
        }

        boolean estabaSobreUmbral = puntajeAnterior.compareTo(umbral) >= 0;
        boolean estaSobreUmbral = puntajeNuevo.compareTo(umbral) >= 0;
        if (estabaSobreUmbral == estaSobreUmbral) {
            return;
        }

        String mensaje = estaSobreUmbral
                ? "PostgresPulse: la fuente '%s' se recupero por encima del umbral de alerta (%s >= %s)"
                        .formatted(fuente.getNombre(), puntajeNuevo, umbral)
                : "PostgresPulse: la fuente '%s' cruzo por debajo del umbral de alerta (%s < %s)"
                        .formatted(fuente.getNombre(), puntajeNuevo, umbral);

        despacharEmail(fuente, mensaje);
        despacharSlack(mensaje);
        despacharPagerDuty(fuente, mensaje, estaSobreUmbral);
    }

    private void despacharEmail(FuenteDatos fuente, String mensaje) {
        if (!propiedades.isEmailHabilitado()) {
            return;
        }
        try {
            SimpleMailMessage correo = new SimpleMailMessage();
            correo.setFrom(propiedades.getEmailDesde());
            correo.setTo(propiedades.getEmailPara());
            correo.setSubject("Alerta PostgresPulse: " + fuente.getNombre());
            correo.setText(mensaje);
            javaMailSender.send(correo);
        } catch (Exception ex) {
            REGISTRO.warn("No se pudo enviar la alerta por email para la fuente {}", fuente.getId(), ex);
        }
    }

    private void despacharSlack(String mensaje) {
        String webhook = propiedades.getSlackWebhookUrl();
        if (webhook == null || webhook.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(webhook)
                    .body(Map.of("text", mensaje))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            REGISTRO.warn("No se pudo enviar la alerta a Slack", ex);
        }
    }

    private void despacharPagerDuty(FuenteDatos fuente, String mensaje, boolean recuperacion) {
        String routingKey = propiedades.getPagerdutyRoutingKey();
        if (routingKey == null || routingKey.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(URL_PAGERDUTY)
                    .body(Map.of(
                            "routing_key", routingKey,
                            "event_action", recuperacion ? "resolve" : "trigger",
                            "dedup_key", "postgrespulse-fuente-" + fuente.getId(),
                            "payload", Map.of(
                                    "summary", mensaje,
                                    "source", "postgrespulse",
                                    "severity", recuperacion ? "info" : "warning")))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            REGISTRO.warn("No se pudo enviar la alerta a PagerDuty para la fuente {}", fuente.getId(), ex);
        }
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeout() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) TIMEOUT.toMillis());
        fabrica.setReadTimeout((int) TIMEOUT.toMillis());
        return fabrica;
    }
}
