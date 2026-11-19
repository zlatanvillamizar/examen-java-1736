package com.carmotors.dao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Fuente de conexiones JDBC para los DAO de inventario.
 * Por defecto es {@code ConexionFactory::obtenerConexion}; en las pruebas de
 * integración permite usar una base de prueba.
 */
@FunctionalInterface
public interface FuenteConexion {
    Connection obtener() throws SQLException;
}
