package com.carmotors.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Prepara las tablas de inventario en una base MySQL de <b>prueba</b> para las
 * pruebas de integración de los DAO.
 *
 * <p>La conexión se configura con {@code CARMOTORS_TEST_DB_URL} (por ejemplo
 * {@code jdbc:mysql://localhost:3306/carmotors_pruebas}),
 * {@code CARMOTORS_TEST_DB_USER} y {@code CARMOTORS_TEST_DB_PASSWORD}.
 * <b>Borra y recrea las tablas de inventario</b>: por seguridad se niega a
 * hacerlo si el nombre de la base no contiene "prueba".</p>
 */
final class EsquemaInventarioPrueba {

    static final String VARIABLE_URL = "CARMOTORS_TEST_DB_URL";

    private static final Path SCRIPT_INVENTARIO = Path.of("database", "bloques", "inventario.sql");
    private static final List<String> TABLAS_EN_ORDEN_DE_BORRADO = List.of(
            "evaluaciones_proveedor", "proveedores_repuestos", "lotes",
            "repuestos", "proveedores", "repuesto_tipos");

    private EsquemaInventarioPrueba() {
    }

    static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                System.getenv(VARIABLE_URL),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
    }

    /** Borra las tablas de inventario y las crea de nuevo con {@code inventario.sql}. */
    static void recrear() throws SQLException, IOException {
        String script = Files.readString(SCRIPT_INVENTARIO, StandardCharsets.UTF_8);
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            String base = conexion.getCatalog();
            if (base == null || !base.contains("prueba")) {
                throw new IllegalStateException(
                        "Por seguridad, la base de prueba debe tener 'prueba' en el nombre: " + base);
            }
            sentencia.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String tabla : TABLAS_EN_ORDEN_DE_BORRADO) {
                sentencia.execute("DROP TABLE IF EXISTS " + tabla);
            }
            sentencia.execute("SET FOREIGN_KEY_CHECKS = 1");
            for (String instruccion : instrucciones(script)) {
                sentencia.execute(instruccion);
            }
        }
    }

    /** Ejecuta una instrucción SQL suelta en la base de prueba (para datos de apoyo). */
    static void ejecutar(String sql) throws SQLException {
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }

    /** Divide el script en instrucciones, sin comentarios ni el USE de la base real. */
    private static List<String> instrucciones(String script) {
        StringBuilder limpio = new StringBuilder();
        for (String linea : script.split("\\R")) {
            String recortada = linea.strip();
            if (recortada.startsWith("--") || recortada.toLowerCase().startsWith("use ")) {
                continue;
            }
            limpio.append(linea).append('\n');
        }
        return Arrays.stream(limpio.toString().split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
