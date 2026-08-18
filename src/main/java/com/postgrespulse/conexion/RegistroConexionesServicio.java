package com.postgrespulse.conexion;

import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.servicio.cifrado.CifradoServicio;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RegistroConexionesServicio {

    private static final int TIEMPO_CONEXION_SEGUNDOS = 5;
    private static final int TIEMPO_SOCKET_SEGUNDOS = 30;
    private static final int TIEMPO_ESPERA_POOL_MS = 5000;
    private static final int MAX_CONEXIONES_POR_FUENTE = 4;

    private final CifradoServicio cifradoServicio;
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public RegistroConexionesServicio(CifradoServicio cifradoServicio) {
        this.cifradoServicio = cifradoServicio;
    }

    public HikariDataSource obtenerOCrear(FuenteDatos fuente) {
        return pools.computeIfAbsent(fuente.getId(), id -> crearPool(fuente));
    }

    public void eliminar(Long fuenteId) {
        HikariDataSource pool = pools.remove(fuenteId);
        if (pool != null) {
            pool.close();
        }
    }

    @PreDestroy
    public void cerrarTodo() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private HikariDataSource crearPool(FuenteDatos fuente) {
        HikariConfig configuracion = new HikariConfig();
        configuracion.setJdbcUrl(armarUrlJdbc(fuente));
        configuracion.setUsername(fuente.getUsuario());
        configuracion.setPassword(cifradoServicio.descifrar(fuente.getContrasenaCifrada()));
        // setReadOnly(true) es solo una señal para el driver/Hikari (p.ej. para
        // enrutar a replicas); pgjdbc NO garantiza por si solo que el servidor
        // rechace escrituras (verificado con RegistroConexionesServicioSoloLecturaTest,
        // que fallo hasta que se agrego el SET explicito de abajo). La garantia real
        // de solo-lectura (ADR-2 de docs/SPECS.md) es esta sesion fijada a nivel de
        // PostgreSQL, que aplica a todas las transacciones de la conexion sin
        // depender del comportamiento por defecto del driver.
        configuracion.setReadOnly(true);
        configuracion.setConnectionTimeout(TIEMPO_ESPERA_POOL_MS);
        configuracion.setMaximumPoolSize(MAX_CONEXIONES_POR_FUENTE);
        configuracion.setPoolName("pulse-" + fuente.getId());
        configuracion.setConnectionInitSql(
                "SET statement_timeout = " + (TIEMPO_SOCKET_SEGUNDOS * 1000) + "; "
                        + "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
        return new HikariDataSource(configuracion);
    }

    private String armarUrlJdbc(FuenteDatos fuente) {
        return "jdbc:postgresql://" + fuente.getHost() + ":" + fuente.getPuerto() + "/" + fuente.getNombreBd()
                + "?connectTimeout=" + TIEMPO_CONEXION_SEGUNDOS
                + "&socketTimeout=" + TIEMPO_SOCKET_SEGUNDOS;
    }
}
