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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Base de datos MySQL <b>desechable</b> para las pruebas de integración de los DAO.
 *
 * <p>Cada instancia crea una base nueva con nombre único
 * ({@code carmotors_prueba_<aleatorio>}), crea ahí las tablas con
 * {@code database/bloques/inventario.sql} y la elimina completa en
 * {@link #close()}. <b>Nunca borra tablas ni datos de una base que ya
 * existía</b>: {@code CREATE DATABASE} falla si el nombre ya está en uso, y
 * solo se elimina la base que esta misma instancia creó.</p>
 *
 * <p>Configuración (las pruebas se saltan si no existe la URL):</p>
 * <ul>
 *   <li>{@code CARMOTORS_TEST_DB_URL}: servidor MySQL, por ejemplo
 *       {@code jdbc:mysql://localhost:3306/}. La base indicada en la URL, si la hay, no se toca.</li>
 *   <li>{@code CARMOTORS_TEST_DB_USER} y {@code CARMOTORS_TEST_DB_PASSWORD}: usuario con permiso
 *       {@code CREATE} y {@code DROP} sobre las bases {@code carmotors_prueba_%}.</li>
 * </ul>
 */
public final class EsquemaInventarioPrueba implements AutoCloseable {

    public static final String VARIABLE_URL = "CARMOTORS_TEST_DB_URL";

    private static final String PREFIJO = "carmotors_prueba_";
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^carmotors_prueba_[0-9a-f]{16}$");
    private static final Path SCRIPT_INVENTARIO = Path.of("database", "bloques", "inventario.sql");

    private final String base;
    private boolean eliminada;

    private EsquemaInventarioPrueba(String base) {
        this.base = base;
    }

    /** Crea una base desechable vacía con las tablas de inventario. */
    public static EsquemaInventarioPrueba crear() throws SQLException, IOException {
        String nombre = PREFIJO + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        verificarNombre(nombre);
        try (Connection conexion = conectarAlServidor(); Statement sentencia = conexion.createStatement()) {
            // Sin IF NOT EXISTS: si por casualidad el nombre ya existe, falla en vez de reutilizarla.
            sentencia.execute("CREATE DATABASE `" + nombre + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        EsquemaInventarioPrueba esquema = new EsquemaInventarioPrueba(nombre);
        try {
            esquema.crearTablas();
        } catch (SQLException | IOException | RuntimeException e) {
            esquema.close();
            throw e;
        }
        return esquema;
    }

    /** Conexión a la base desechable; úsala como {@link FuenteConexion} de los DAO. */
    public Connection conectar() throws SQLException {
        if (eliminada) {
            throw new IllegalStateException("La base de prueba ya fue eliminada");
        }
        Connection conexion = conectarAlServidor();
        conexion.setCatalog(base);
        return conexion;
    }

    /** Ejecuta una instrucción SQL en la base desechable (para datos de apoyo). */
    public void ejecutar(String sql) throws SQLException {
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        }
    }

    public String nombre() {
        return base;
    }

    /** Elimina la base desechable que creó esta instancia (y solo esa). */
    @Override
    public void close() throws SQLException {
        if (eliminada) {
            return;
        }
        verificarNombre(base);
        try (Connection conexion = conectarAlServidor(); Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP DATABASE IF EXISTS `" + base + "`");
        }
        eliminada = true;
    }

    private void crearTablas() throws SQLException, IOException {
        String script = Files.readString(SCRIPT_INVENTARIO, StandardCharsets.UTF_8);
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            for (String instruccion : instrucciones(script)) {
                sentencia.execute(instruccion);
            }
        }
    }

    private static Connection conectarAlServidor() throws SQLException {
        return DriverManager.getConnection(
                System.getenv(VARIABLE_URL),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
    }

    private static void verificarNombre(String nombre) {
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new IllegalStateException("Nombre de base de prueba no permitido: " + nombre);
        }
    }

    /**
     * Divide el script en instrucciones, sin comentarios ni el {@code USE}
     * (así nunca cambia a la base real {@code carmotors}).
     */
    private static List<String> instrucciones(String script) {
        StringBuilder limpio = new StringBuilder();
        for (String linea : script.split("\\R")) {
            String recortada = linea.strip();
            if (recortada.startsWith("--") || recortada.toLowerCase().startsWith("use ")) {
                continue;
            }
            limpio.append(linea).append('\n');
        }
        List<String> instrucciones = Arrays.stream(limpio.toString().split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        for (String instruccion : instrucciones) {
            String mayusculas = instruccion.toUpperCase();
            if (mayusculas.startsWith("DROP") || mayusculas.startsWith("TRUNCATE") || mayusculas.startsWith("DELETE")) {
                throw new IllegalStateException("El script de inventario no debe borrar datos: " + instruccion);
            }
        }
        return instrucciones;
    }
}
