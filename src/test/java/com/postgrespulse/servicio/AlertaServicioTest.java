package com.postgrespulse.servicio;

import com.postgrespulse.config.PropiedadesAlertas;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlertaServicioTest {

    private static final String SLACK_WEBHOOK = "https://hooks.slack.com/services/T00/B00/xxx";
    private static final String PAGERDUTY_ROUTING_KEY = "clave-de-ruteo";
    private static final String PAGERDUTY_URL = "https://events.pagerduty.com/v2/enqueue";

    private PropiedadesAlertas propiedades;
    private JavaMailSender javaMailSender;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer servidorSimulado;

    @BeforeEach
    void configurar() {
        propiedades = new PropiedadesAlertas();
        propiedades.setEmailHabilitado(true);
        propiedades.setEmailDesde("alertas@postgrespulse.local");
        propiedades.setEmailPara("oncall@postgrespulse.local");
        propiedades.setSlackWebhookUrl(SLACK_WEBHOOK);
        propiedades.setPagerdutyRoutingKey(PAGERDUTY_ROUTING_KEY);

        javaMailSender = mock(JavaMailSender.class);
        restClientBuilder = RestClient.builder();
        servidorSimulado = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private AlertaServicio crearServicio() {
        return new AlertaServicio(propiedades, javaMailSender, restClientBuilder);
    }

    private FuenteDatos fuenteConUmbral(BigDecimal umbral) {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(1L);
        fuente.setNombre("Ventas");
        fuente.setUmbralAlerta(umbral);
        return fuente;
    }

    private Analisis analisisConPuntaje(BigDecimal puntaje) {
        Analisis analisis = new Analisis();
        analisis.setPuntajeSalud(puntaje);
        return analisis;
    }

    @Test
    void noDisparaSinUmbralConfigurado() {
        FuenteDatos fuente = fuenteConUmbral(null);
        Analisis anterior = analisisConPuntaje(new BigDecimal("80.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("30.00"));

        crearServicio().evaluar(fuente, anterior, nuevo);

        verifyNoInteractions(javaMailSender);
        servidorSimulado.verify();
    }

    @Test
    void noDisparaSinAnalisisAnterior() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("30.00"));

        crearServicio().evaluar(fuente, null, nuevo);

        verifyNoInteractions(javaMailSender);
        servidorSimulado.verify();
    }

    @Test
    void noDisparaSinCruceDeUmbral() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anterior = analisisConPuntaje(new BigDecimal("90.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("70.00"));

        crearServicio().evaluar(fuente, anterior, nuevo);

        verifyNoInteractions(javaMailSender);
        servidorSimulado.verify();
    }

    @Test
    void disparaEnCruceHaciaAbajo() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anterior = analisisConPuntaje(new BigDecimal("80.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("30.00"));

        servidorSimulado.expect(requestTo(SLACK_WEBHOOK)).andRespond(withSuccess());
        servidorSimulado.expect(requestTo(PAGERDUTY_URL))
                .andExpect(content().string(containsString("\"event_action\":\"trigger\"")))
                .andRespond(withSuccess());

        crearServicio().evaluar(fuente, anterior, nuevo);

        verify(javaMailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        servidorSimulado.verify();
    }

    @Test
    void disparaEnRecuperacion() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anterior = analisisConPuntaje(new BigDecimal("30.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("80.00"));

        servidorSimulado.expect(requestTo(SLACK_WEBHOOK)).andRespond(withSuccess());
        servidorSimulado.expect(requestTo(PAGERDUTY_URL))
                .andExpect(content().string(containsString("\"event_action\":\"resolve\"")))
                .andRespond(withSuccess());

        crearServicio().evaluar(fuente, anterior, nuevo);

        verify(javaMailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        servidorSimulado.verify();
    }

    @Test
    void unCanalCaidoNoBloqueaLosDemas() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anterior = analisisConPuntaje(new BigDecimal("80.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("30.00"));

        org.mockito.Mockito.doThrow(new MailSendException("SMTP no disponible"))
                .when(javaMailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        servidorSimulado.expect(requestTo(SLACK_WEBHOOK)).andRespond(withServerError());
        servidorSimulado.expect(requestTo(PAGERDUTY_URL)).andRespond(withSuccess());

        crearServicio().evaluar(fuente, anterior, nuevo);

        verify(javaMailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        servidorSimulado.verify();
    }

    @Test
    void noDisparaCanalesDeshabilitados() {
        propiedades = new PropiedadesAlertas();
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anterior = analisisConPuntaje(new BigDecimal("80.00"));
        Analisis nuevo = analisisConPuntaje(new BigDecimal("30.00"));

        crearServicio().evaluar(fuente, anterior, nuevo);

        verifyNoInteractions(javaMailSender);
        servidorSimulado.verify();
    }

    @Test
    void puntajeAnteriorONuevoNuloNoDispara() {
        FuenteDatos fuente = fuenteConUmbral(new BigDecimal("60.00"));
        Analisis anteriorSinPuntaje = analisisConPuntaje(null);
        Analisis nuevoSinPuntaje = analisisConPuntaje(null);

        crearServicio().evaluar(fuente, anteriorSinPuntaje, analisisConPuntaje(new BigDecimal("30.00")));
        crearServicio().evaluar(fuente, analisisConPuntaje(new BigDecimal("80.00")), nuevoSinPuntaje);

        verifyNoInteractions(javaMailSender);
        servidorSimulado.verify();
    }
}
