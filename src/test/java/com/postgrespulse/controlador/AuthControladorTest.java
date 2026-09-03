package com.postgrespulse.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.config.JacksonConfig;
import com.postgrespulse.dto.LoginDto;
import com.postgrespulse.dto.TokenRespuestaDto;
import com.postgrespulse.excepcion.CredencialesInvalidasException;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import com.postgrespulse.seguridad.CookieJwtFabrica;
import com.postgrespulse.seguridad.ResolvedorIpCliente;
import com.postgrespulse.servicio.AutenticacionServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthControlador.class)
@AutoConfigureMockMvc(addFilters = false)
// @WebMvcTest no trae el ObjectMapper de JacksonConfig (component scan
// restringido a controladores/beans web) -- ver el javadoc de JacksonConfig
// para el porque de ese bean.
@Import(JacksonConfig.class)
class AuthControladorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

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
    }

    @Test
    void loginExitosoDevuelveTokenYCookie() throws Exception {
        when(controlIntentos.tiempoRestanteBloqueo(anyString())).thenReturn(Duration.ZERO);
        when(autenticacionServicio.autenticar(any(LoginDto.class), anyString()))
                .thenReturn(TokenRespuestaDto.de("jwt-de-prueba", OffsetDateTime.now().plusHours(8)));
        when(cookieJwtFabrica.crear("jwt-de-prueba"))
                .thenReturn(ResponseCookie.from("PULSE_JWT", "jwt-de-prueba").build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginDto("admin", "admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-de-prueba"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void loginConCuerpoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginDto("", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void loginBloqueadoPorFuerzaBrutaDevuelve429ConRetryAfter() throws Exception {
        when(controlIntentos.tiempoRestanteBloqueo(anyString())).thenReturn(Duration.ofSeconds(45));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginDto("admin", "admin"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        when(controlIntentos.tiempoRestanteBloqueo(anyString())).thenReturn(Duration.ZERO);
        when(autenticacionServicio.autenticar(any(LoginDto.class), anyString()))
                .thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginDto("admin", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void logoutLimpiaLaCookie() throws Exception {
        when(cookieJwtFabrica.limpiar()).thenReturn(ResponseCookie.from("PULSE_JWT", "").maxAge(0).build());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"));
    }
}
