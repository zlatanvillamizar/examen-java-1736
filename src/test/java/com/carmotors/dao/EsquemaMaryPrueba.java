package com.carmotors.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

final class EsquemaMaryPrueba implements AutoCloseable {
    private static final Pattern REFERENCIA_EXTERNA = Pattern.compile(
            "(?i)(?:`[^`]+`|[a-z_$][\\w$]*)\\s*\\.\\s*(?:`[^`]+`|[a-z_$][\\w$]*)");
    private final String base = "carmotors_prueba_" + UUID.randomUUID().toString().replace("-", "");

    static EsquemaMaryPrueba crear() throws IOException, SQLException {
        String ruta = Objects.requireNonNull(System.getenv("CARMOTORS_TEST_DB_SCHEMA"),
                "Indique la ruta del SQL consolidado");
        List<String> instrucciones = instrucciones(Files.readString(Path.of(ruta)));
        var esquema = new EsquemaMaryPrueba();
        try (var con = conectarAlServidor(); var st = con.createStatement()) {
            st.execute("CREATE DATABASE " + esquema.base + " CHARACTER SET utf8mb4");
        }
        try {
            try (var con = esquema.conectar(); var st = con.createStatement()) {
                for (String instruccion : instrucciones) {
                    st.execute(instruccion);
                }
            }
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
        Connection con = conectarAlServidor();
        try {
            con.setCatalog(base);
            return con;
        } catch (SQLException e) {
            try {
                con.close();
            } catch (SQLException cierre) {
                e.addSuppressed(cierre);
            }
            throw e;
        }
    }

    int insertar(String sql) throws SQLException {
        try (var con = conectar(); var st = con.createStatement()) {
            st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (var claves = st.getGeneratedKeys()) {
                if (!claves.next()) throw new SQLException("La inserción no devolvió un ID");
                return claves.getInt(1);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        try (var con = conectarAlServidor(); var st = con.createStatement()) {
            st.execute("DROP DATABASE " + base);
        }
    }

    private static Connection conectarAlServidor() throws SQLException {
        return DriverManager.getConnection(
                Objects.requireNonNull(System.getenv("CARMOTORS_TEST_DB_URL"),
                        "Indique un servidor MySQL de pruebas"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
    }

    // El SQL puede declarar su catálogo original. Las tablas se crean en el catálogo temporal.
    private static List<String> instrucciones(String script) {
        String limpio = script.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)^\\s*(?:--|#)[^\\r\\n]*", "");
        List<String> instrucciones = Arrays.stream(limpio.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.matches("(?is)(?:USE|CREATE\\s+(?:DATABASE|SCHEMA))\\s+.*"))
                .toList();
        for (String instruccion : instrucciones) {
            String sql = instruccion.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!(sql.startsWith("CREATE TABLE ") || sql.startsWith("ALTER TABLE ")
                    || sql.startsWith("CREATE INDEX ") || sql.startsWith("CREATE UNIQUE INDEX ")
                    || sql.startsWith("INSERT INTO ") || sql.startsWith("INSERT IGNORE INTO "))
                    || REFERENCIA_EXTERNA.matcher(instruccion.replaceAll("'(?:''|[^'])*'", "''")).find()) {
                throw new IllegalArgumentException("SQL de esquema temporal no admitido: " + instruccion);
            }
        }
        return instrucciones;
    }
}
