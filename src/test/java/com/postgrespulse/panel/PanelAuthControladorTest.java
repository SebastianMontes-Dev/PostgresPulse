package com.postgrespulse.panel;

import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.CredencialesInvalidasException;
import com.postgrespulse.excepcion.DemasiadosIntentosException;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.CookieJwtFabrica;
import com.postgrespulse.seguridad.ResolvedorIpCliente;
import com.postgrespulse.servicio.AutenticacionServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * PanelAuthControlador no tenia ningun test propio -- solo se ejercitaba
 * indirectamente via login por API en otros tests de integracion. Cubre las
 * 3 rutas del formulario del panel (GET /login, POST /login, POST /logout),
 * incluyendo los dos caminos que muestran el mismo mensaje de error generico
 * (credenciales invalidas y bloqueo por fuerza bruta reportado recien al
 * intentar autenticar, no antes).
 */
@WebMvcTest(PanelAuthControlador.class)
@AutoConfigureMockMvc(addFilters = false)
class PanelAuthControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutenticacionServicio autenticacionServicio;
    @MockitoBean
    private ControlIntentosFallidosServicio controlIntentos;
    @MockitoBean
    private CookieJwtFabrica cookieJwtFabrica;
    @MockitoBean
    private ResolvedorIpCliente resolvedorIpCliente;

    @BeforeEach
    void resolverIpPorDefecto() {
        lenient().when(resolvedorIpCliente.resolver(any())).thenReturn("127.0.0.1");
        lenient().when(controlIntentos.tiempoRestanteBloqueo(anyString())).thenReturn(Duration.ZERO);
    }

    @Test
    void formularioMuestraLaVistaDeLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginExitosoRedirigeYSeteaLaCookie() throws Exception {
        when(autenticacionServicio.autenticar(any(LoginDto.class), eq("127.0.0.1")))
                .thenReturn(TokenRespuestaDto.de("jwt-de-prueba", OffsetDateTime.now().plusHours(8)));
        when(cookieJwtFabrica.crear("jwt-de-prueba"))
                .thenReturn(ResponseCookie.from("PULSE_JWT", "jwt-de-prueba").build());

        mockMvc.perform(post("/login").param("usuario", "admin").param("contrasena", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void loginConCredencialesInvalidasMuestraErrorGenerico() throws Exception {
        when(autenticacionServicio.autenticar(any(LoginDto.class), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/login").param("usuario", "admin").param("contrasena", "incorrecta"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "Usuario o contraseña incorrectos"));
    }

    @Test
    void loginBloqueadoDesdeAntesMuestraSegundosRestantes() throws Exception {
        when(controlIntentos.tiempoRestanteBloqueo(anyString())).thenReturn(Duration.ofSeconds(45));

        mockMvc.perform(post("/login").param("usuario", "admin").param("contrasena", "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "Demasiados intentos fallidos. Reintenta en 45 segundos."));

        verify(autenticacionServicio, org.mockito.Mockito.never()).autenticar(any(), anyString());
    }

    @Test
    void bloqueoLanzadoAlAutenticarTambienMuestraElErrorGenerico() throws Exception {
        when(autenticacionServicio.autenticar(any(LoginDto.class), anyString()))
                .thenThrow(new DemasiadosIntentosException(30));

        mockMvc.perform(post("/login").param("usuario", "admin").param("contrasena", "admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "Usuario o contraseña incorrectos"));
    }

    @Test
    void logoutLimpiaLaCookieYRedirigeAlLogin() throws Exception {
        when(cookieJwtFabrica.limpiar()).thenReturn(ResponseCookie.from("PULSE_JWT", "").maxAge(0).build());

        mockMvc.perform(post("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(header().exists("Set-Cookie"));
    }
}
