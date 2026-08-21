package com.postgrespulse.seguridad;

import com.postgrespulse.dominio.Rol;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtServicio jwtServicio;

    private JwtAuthenticationFilter filtro;
    private final JwtServicio.ClaimsSesion claimsAdmin =
            new JwtServicio.ClaimsSesion("ana", Rol.ADMIN, OffsetDateTime.now().plusHours(1));

    @BeforeEach
    void configurar() {
        filtro = new JwtAuthenticationFilter(jwtServicio);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaUnaRutaDeApiConElHeaderAuthorization() throws Exception {
        when(jwtServicio.validar("token-valido")).thenReturn(Optional.of(claimsAdmin));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.addHeader("Authorization", "Bearer token-valido");

        filtro.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("ana");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void unaRutaDeApiIgnoraLaCookieAunqueSeaValida() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NOMBRE, "token-valido"));

        filtro.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unaRutaDelPanelSeAutenticaConLaCookie() throws Exception {
        when(jwtServicio.validar("token-valido")).thenReturn(Optional.of(claimsAdmin));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fuentes/1");
        request.setCookies(new Cookie(JwtAuthenticationFilter.COOKIE_NOMBRE, "token-valido"));

        filtro.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void unTokenInvalidoNoAutenticaYNoRompeLaCadena() throws Exception {
        when(jwtServicio.validar("token-invalido")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.addHeader("Authorization", "Bearer token-invalido");
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilterInternal(request, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void sinTokenNoAutenticaYContinuaLaCadena() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");

        filtro.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
