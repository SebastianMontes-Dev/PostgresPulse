package com.postgrespulse.panel;

import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.CredencialesInvalidasException;
import com.postgrespulse.excepcion.DemasiadosIntentosException;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.CookieJwtFabrica;
import com.postgrespulse.seguridad.ResolvedorIpCliente;
import com.postgrespulse.servicio.AutenticacionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

/**
 * Login del panel (formulario HTML) -- distinto de AuthControlador
 * (POST /api/v1/auth/login, JSON) pero delega en el mismo AutenticacionServicio:
 * no se duplica la verificación de credenciales, solo la forma de entrada/salida
 * (formulario + redirect con cookie, en vez de JSON).
 */
@Controller
public class PanelAuthControlador {

    private final AutenticacionServicio autenticacionServicio;
    private final ControlIntentosFallidosServicio controlIntentos;
    private final CookieJwtFabrica cookieJwtFabrica;
    private final ResolvedorIpCliente resolvedorIpCliente;

    public PanelAuthControlador(AutenticacionServicio autenticacionServicio,
                                 ControlIntentosFallidosServicio controlIntentos,
                                 CookieJwtFabrica cookieJwtFabrica,
                                 ResolvedorIpCliente resolvedorIpCliente) {
        this.autenticacionServicio = autenticacionServicio;
        this.controlIntentos = controlIntentos;
        this.cookieJwtFabrica = cookieJwtFabrica;
        this.resolvedorIpCliente = resolvedorIpCliente;
    }

    @GetMapping("/login")
    public String formulario() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String usuario, @RequestParam String contrasena,
                         HttpServletRequest peticion, HttpServletResponse respuesta, Model modelo) {
        String ip = resolvedorIpCliente.resolver(peticion);
        Duration restante = controlIntentos.tiempoRestanteBloqueo(ip);
        if (!restante.isZero()) {
            modelo.addAttribute("error", "Demasiados intentos fallidos. Reintenta en "
                    + Math.max(1, restante.toSeconds()) + " segundos.");
            return "login";
        }

        try {
            TokenRespuestaDto token = autenticacionServicio.autenticar(new LoginDto(usuario, contrasena), ip);
            ResponseCookie cookie = cookieJwtFabrica.crear(token.token());
            respuesta.addHeader("Set-Cookie", cookie.toString());
            return "redirect:/";
        } catch (CredencialesInvalidasException | DemasiadosIntentosException ex) {
            modelo.addAttribute("error", "Usuario o contraseña incorrectos");
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse respuesta) {
        ResponseCookie cookie = cookieJwtFabrica.limpiar();
        respuesta.addHeader("Set-Cookie", cookie.toString());
        return "redirect:/login";
    }
}
