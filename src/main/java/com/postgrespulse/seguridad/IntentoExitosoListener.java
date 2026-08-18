package com.postgrespulse.seguridad;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Un login exitoso limpia el contador de fallos de esa IP (no arrastra intentos previos de terceros en la misma red). */
@Component
public class IntentoExitosoListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final ControlIntentosFallidosServicio control;

    public IntentoExitosoListener(ControlIntentosFallidosServicio control) {
        this.control = control;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails detalles) {
            control.registrarExito(detalles.getRemoteAddress());
        }
    }
}
