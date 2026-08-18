package com.postgrespulse.seguridad;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Spring Security publica este evento en cada fallo de Basic Auth (credenciales invalidas, usuario inexistente). */
@Component
public class IntentoFallidoListener implements ApplicationListener<AbstractAuthenticationFailureEvent> {

    private final ControlIntentosFallidosServicio control;

    public IntentoFallidoListener(ControlIntentosFallidosServicio control) {
        this.control = control;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationFailureEvent event) {
        if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails detalles) {
            control.registrarFallo(detalles.getRemoteAddress());
        }
    }
}
