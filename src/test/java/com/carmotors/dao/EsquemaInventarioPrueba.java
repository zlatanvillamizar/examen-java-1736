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
    private static final Path SCRIPT_CONSOLIDADO = Path.of("database", "esquema", "esquema.sql");
    /** Bloques necesarios para {@code servicios_repuestos} y sus claves foráneas (solo lectura). */
    private static final List<Path> SCRIPTS_SERVICIOS = List.of(
            Path.of("database", "bloques", "clientes.sql"),
            Path.of("database", "bloques", "vehiculos.sql"),
            Path.of("database", "bloques", "servicios.sql"));

    private final String base;
    private boolean eliminada;

    private EsquemaInventarioPrueba(String base) {
        this.base = base;
    }

    /** Crea una base desechable vacía con las tablas de inventario. */
    public static EsquemaInventarioPrueba crear() throws SQLException, IOException {
        return crear(SCRIPT_INVENTARIO, false);
    }

    /**
     * Crea una base desechable con las tablas de inventario y, además, las de clientes,
     * vehículos y servicios (incluida {@code servicios_repuestos}), leídas de
     * {@code database/bloques/} sin modificarlas.
     */
    public static EsquemaInventarioPrueba crearConServicios() throws SQLException, IOException {
        return crear(SCRIPT_INVENTARIO, true);
    }

    /** Instala el DDL de entrega completo en una base desechable vacía. */
    public static EsquemaInventarioPrueba crearConsolidado() throws SQLException, IOException {
        return crear(SCRIPT_CONSOLIDADO, false);
    }

    private static EsquemaInventarioPrueba crear(Path script, boolean conServicios)
            throws SQLException, IOException {
        String nombre = PREFIJO + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        verificarNombre(nombre);
        try (Connection conexion = conectarAlServidor(); Statement sentencia = conexion.createStatement()) {
            // Sin IF NOT EXISTS: si por casualidad el nombre ya existe, falla en vez de reutilizarla.
            sentencia.execute("CREATE DATABASE `" + nombre + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        EsquemaInventarioPrueba esquema = new EsquemaInventarioPrueba(nombre);
        try {
            esquema.crearTablas(script);
            if (conServicios) {
                esquema.crearTablasDeServicios();
            }
        } catch (SQLException | IOException | RuntimeException e) {
            try {
                esquema.close();
            } catch (SQLException limpieza) {
                e.addSuppressed(limpieza);
            }
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

    private void crearTablas(Path ruta) throws SQLException, IOException {
        String script = Files.readString(ruta, StandardCharsets.UTF_8);
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            for (String instruccion : instrucciones(script)) {
                sentencia.execute(instruccion);
            }
        }
    }

    /**
     * Ejecuta los bloques de clientes, vehículos y servicios. Sus dependencias se cruzan
     * entre archivos (por ejemplo, {@code clientes.sql} referencia {@code vehiculos} y
     * {@code servicios}), así que las instrucciones que fallan por una tabla aún no creada
     * se reintentan mientras haya progreso, sin suponer un orden fijo.
     */
    private void crearTablasDeServicios() throws SQLException, IOException {
        List<String> pendientes = new java.util.ArrayList<>();
        for (Path script : SCRIPTS_SERVICIOS) {
            pendientes.addAll(instrucciones(Files.readString(script, StandardCharsets.UTF_8)));
        }
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            SQLException ultimoError = null;
            boolean progreso = true;
            while (!pendientes.isEmpty() && progreso) {
                progreso = false;
                for (java.util.Iterator<String> it = pendientes.iterator(); it.hasNext(); ) {
                    String instruccion = it.next();
                    try {
                        sentencia.execute(instruccion);
                        it.remove();
                        progreso = true;
                    } catch (SQLException e) {
                        ultimoError = e;
                    }
                }
            }
            if (!pendientes.isEmpty()) {
                throw new SQLException("No se pudieron crear las tablas de servicios: " + pendientes.get(0), ultimoError);
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
                throw new IllegalStateException("El script no debe borrar datos: " + instruccion);
            }
            if (mayusculas.matches("(?s)CREATE\\s+(DATABASE|SCHEMA).*")) {
                throw new IllegalStateException("El script no debe crear bases de datos: " + instruccion);
            }
            if (mayusculas.matches("(?s).*\\bCARMOTORS\\s*`?\\s*\\..*")) {
                throw new IllegalStateException("El script no debe referirse a la base carmotors: " + instruccion);
            }
        }
        return instrucciones;
    }
}
