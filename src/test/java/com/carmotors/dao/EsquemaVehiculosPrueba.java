package com.carmotors.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Esquema desechable con las dos tablas aprobadas que usa el módulo. */
final class EsquemaVehiculosPrueba implements AutoCloseable {
    static final String VARIABLE_URL = "CARMOTORS_TEST_DB_URL";
    private static final Path BLOQUES = Path.of("database", "bloques");
    private final String nombre = "carmotors_prueba_" + UUID.randomUUID().toString().replace("-", "");

    static EsquemaVehiculosPrueba crear() throws IOException, SQLException {
        String clientes = leerTabla("clientes");
        String vehiculos = leerTabla("vehiculos");
        EsquemaVehiculosPrueba esquema = new EsquemaVehiculosPrueba();
        try (Connection conexion = conectarAlServidor(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + esquema.nombre + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try {
            esquema.ejecutar(clientes);
            esquema.ejecutar(vehiculos);
            return esquema;
        } catch (SQLException | RuntimeException e) {
            try {
                esquema.close();
            } catch (SQLException limpieza) {
                e.addSuppressed(limpieza);
            }
            throw e;
        }
    }

    Connection conectar() throws SQLException {
        Connection conexion = conectarAlServidor();
        try {
            conexion.setCatalog(nombre);
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

    void ejecutar(String sql) throws SQLException {
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }

    @Override
    public void close() throws SQLException {
        try (Connection conexion = conectarAlServidor(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP DATABASE " + nombre);
        }
    }

    private static Connection conectarAlServidor() throws SQLException {
        return DriverManager.getConnection(System.getenv(VARIABLE_URL),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
    }

    private static String leerTabla(String tabla) throws IOException {
        String script = Files.readString(BLOQUES.resolve(tabla + ".sql"));
        Pattern create = Pattern.compile("(?im)^CREATE TABLE " + Pattern.quote(tabla) + "\\s*\\(");
        for (String instruccion : script.split(";")) {
            var coincidencia = create.matcher(instruccion);
            if (coincidencia.find()) {
                String ddl = instruccion.substring(coincidencia.start()).strip();
                if (ddl.toLowerCase(Locale.ROOT).contains("carmotors.")) {
                    throw new IOException("La tabla de prueba debe usar el esquema desechable: " + tabla);
                }
                return ddl;
            }
        }
        throw new IOException("Falta la definición SQL de " + tabla);
    }
}
