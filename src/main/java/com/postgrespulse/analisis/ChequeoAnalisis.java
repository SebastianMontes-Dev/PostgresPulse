package com.postgrespulse.analisis;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.FuenteDatos;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Estrategia: cada chequeo de diagnostico es una implementacion de esta interfaz.
 * Anadir un chequeo nuevo es agregar una clase con @Component; FabricaChequeos
 * la recoge automaticamente via inyeccion de lista.
 */
public interface ChequeoAnalisis {

    String codigo();

    CategoriaChequeo categoria();

    /**
     * @param conexion conexion de solo lectura ya abierta hacia la fuente objetivo
     * @param fuente   fuente sobre la que se ejecuta (para filtroEsquema, etc.)
     */
    ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException;
}
