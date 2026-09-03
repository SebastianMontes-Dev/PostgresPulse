package com.postgrespulse.seguridad;

import com.postgrespulse.config.PropiedadesSeguridad;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resuelve la IP "real" del cliente para LimiteTasaApiServicio y
 * ControlIntentosFallidosServicio. Por defecto (app.seguridad.proxies-confiables
 * vacio) devuelve request.getRemoteAddr() sin mas -- el mismo comportamiento
 * de siempre. Solo cuando la conexion TCP llega desde una IP/CIDR listada ahi
 * (p.ej. la red interna del reverse proxy Caddy de
 * deploy/docker-compose.prod.yml) confia en X-Forwarded-For/X-Real-IP; de lo
 * contrario cualquier cliente podria falsificar esas cabeceras para evadir el
 * limite de tasa o el bloqueo por fuerza bruta.
 */
@Component
public class ResolvedorIpCliente {

    private final List<IpAddressMatcher> proxiesConfiables;

    public ResolvedorIpCliente(PropiedadesSeguridad propiedadesSeguridad) {
        this.proxiesConfiables = construirMatchers(propiedadesSeguridad.getProxiesConfiables());
    }

    private static List<IpAddressMatcher> construirMatchers(List<String> configurados) {
        if (configurados == null || configurados.isEmpty()) {
            return List.of();
        }
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String cidr : configurados) {
            String valor = cidr.trim();
            if (!valor.isEmpty()) {
                matchers.add(new IpAddressMatcher(valor));
            }
        }
        return matchers;
    }

    public String resolver(HttpServletRequest request) {
        String remoto = request.getRemoteAddr();
        if (remoto == null || proxiesConfiables.isEmpty() || !esProxyConfiable(remoto)) {
            return remoto;
        }
        String reenviada = primeraIp(request.getHeader("X-Forwarded-For"));
        if (reenviada != null) {
            return reenviada;
        }
        String real = request.getHeader("X-Real-IP");
        return (real != null && !real.isBlank()) ? real.trim() : remoto;
    }

    private boolean esProxyConfiable(String ip) {
        for (IpAddressMatcher matcher : proxiesConfiables) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private String primeraIp(String cabecera) {
        if (cabecera == null || cabecera.isBlank()) {
            return null;
        }
        String primera = cabecera.split(",")[0].trim();
        return primera.isEmpty() ? null : primera;
    }
}
