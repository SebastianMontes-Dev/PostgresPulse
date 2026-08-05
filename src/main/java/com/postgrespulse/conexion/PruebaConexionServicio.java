package com.postgrespulse.conexion;

import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dto.PruebaConexionDto;
import com.postgrespulse.excepcion.ConexionFallidaException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

@Service
public class PruebaConexionServicio {

    private final RegistroConexionesServicio registroConexiones;
    private final FuenteDatosRepositorio fuenteDatosRepositorio;

    public PruebaConexionServicio(RegistroConexionesServicio registroConexiones,
                                  FuenteDatosRepositorio fuenteDatosRepositorio) {
        this.registroConexiones = registroConexiones;
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
    }

    public PruebaConexionDto probar(Long fuenteId) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));
        long inicio = System.nanoTime();
        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection();
             Statement declaracion = conexion.createStatement();
             ResultSet resultados = declaracion.executeQuery("SELECT version()")) {
            resultados.next();
            long latencia = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            actualizarEstado(fuente, EstadoFuente.EN_LINEA, null);
            return new PruebaConexionDto(true, latencia, resultados.getString(1), "Conexión exitosa");
        } catch (Exception ex) {
            String error = mensajeLegible(ex);
            actualizarEstado(fuente, EstadoFuente.ERROR, error);
            throw new ConexionFallidaException("No se pudo conectar: " + error);
        }
    }

    private void actualizarEstado(FuenteDatos fuente, EstadoFuente estado, String error) {
        fuente.setEstado(estado);
        fuente.setUltimoError(error);
        fuenteDatosRepositorio.save(fuente);
    }

    private String mensajeLegible(Exception ex) {
        String mensaje = ex.getMessage();
        return mensaje == null ? ex.getClass().getSimpleName() : mensaje;
    }
}
