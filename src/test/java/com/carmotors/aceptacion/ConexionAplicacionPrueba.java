package com.carmotors.aceptacion;

import com.mysql.cj.jdbc.NonRegisteringDriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/** Redirige la factoría JDBC fija únicamente durante una prueba JUnit aislada. */
final class ConexionAplicacionPrueba extends NonRegisteringDriver implements AutoCloseable {
    private static final String URL_APLICACION = "jdbc:mysql://localhost:3306/carmotors";
    private final String urlPrueba;
    private final String esquema;
    private final List<Driver> controladoresMysql;

    ConexionAplicacionPrueba(String urlPrueba, String esquema) throws SQLException {
        this.urlPrueba = urlPrueba;
        this.esquema = esquema;
        controladoresMysql = DriverManager.drivers().filter(NonRegisteringDriver.class::isInstance).toList();
        DriverManager.registerDriver(this);
        try {
            for (Driver controlador : controladoresMysql) {
                DriverManager.deregisterDriver(controlador);
            }
        } catch (SQLException e) {
            try {
                close();
            } catch (SQLException restauracion) {
                e.addSuppressed(restauracion);
            }
            throw e;
        }
    }

    @Override
    public Connection connect(String url, Properties credenciales) throws SQLException {
        if (urlPrueba.equals(url)) {
            return super.connect(url, credenciales);
        }
        if (!URL_APLICACION.equals(url)) {
            return null;
        }
        var prueba = new Properties();
        prueba.setProperty("user", System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"));
        prueba.setProperty("password", System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
        Connection conexion = super.connect(urlPrueba, prueba);
        try {
            conexion.setCatalog(esquema);
            return conexion;
        } catch (SQLException e) {
            try {
                conexion.close();
            } catch (SQLException cierre) {
                e.addSuppressed(cierre);
            }
            throw e;
        }
    }

    @Override
    public void close() throws SQLException {
        DriverManager.deregisterDriver(this);
        for (Driver controlador : controladoresMysql) {
            DriverManager.registerDriver(controlador);
        }
    }
}
