package com.carmotors.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionFactory {

    private ConexionFactory() {
    }

    public static Connection obtenerConexion() throws SQLException {
        String url = System.getenv("CARMOTORS_DB_URL");
        String usuario = System.getenv("CARMOTORS_DB_USER");
        String password = System.getenv("CARMOTORS_DB_PASSWORD");
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank() || password == null) {
            throw new SQLException("Configure CARMOTORS_DB_URL, CARMOTORS_DB_USER y CARMOTORS_DB_PASSWORD");
        }
        return DriverManager.getConnection(url, usuario, password);
    }
}
