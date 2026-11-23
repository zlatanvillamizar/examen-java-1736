package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.servicios.inventario.ConsultaReservas;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de aceptación de Inventario y Proveedores sobre el <b>esquema consolidado</b>
 * ({@code database/esquema/esquema.sql}, PR #70), manejando las vistas por consola tal
 * como lo haría un usuario.
 *
 * <p>Instala el esquema completo en una base desechable, inicializa {@code repuesto_tipos}
 * con {@code docs/inventario/repuesto_tipos_iniciales.sql} (datos, sin tocar el DDL) y
 * comprueba que lo registrado sigue ahí al abrir conexiones nuevas.</p>
 *
 * <p>Solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL} y el esquema consolidado está
 * en el proyecto; mientras {@code esquema.sql} no llegue a la rama, queda omitida.</p>
 */
@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
@EnabledIf("esquemaConsolidadoPublicado")
class AceptacionInventarioProveedoresTest {

    private static final Path ESQUEMA_CONSOLIDADO = Path.of("database", "esquema", "esquema.sql");
    private static final Path TIPOS_INICIALES = Path.of("docs", "inventario", "repuesto_tipos_iniciales.sql");
    private static final int TIPOS_PROPUESTOS = 6;

    private EsquemaInventarioPrueba esquema;

    static boolean esquemaConsolidadoPublicado() {
        return Files.isRegularFile(ESQUEMA_CONSOLIDADO);
    }

    @BeforeEach
    void instalarEsquemaConsolidado() throws SQLException, IOException {
        esquema = EsquemaInventarioPrueba.crearConsolidado();
    }

    @AfterEach
    void eliminarBaseDesechable() throws SQLException {
        if (esquema != null) {
            esquema.close();
        }
    }

    // ---------- utilidades ----------

    /** Ejecuta el script documentado de datos iniciales (sin comentarios, por sentencias). */
    private void inicializarTiposDeRepuesto() throws SQLException, IOException {
        inicializarTiposDeRepuesto(null);
    }

    /**
     * Ejecuta el script en una sola conexión, como el cliente {@code mysql}. Con
     * {@code charsetCliente} se simula un cliente cuya codificación por defecto no es
     * utf8mb4: el servidor interpreta con ella los bytes UTF-8 del archivo.
     */
    private void inicializarTiposDeRepuesto(String charsetCliente) throws SQLException, IOException {
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : Files.readAllLines(TIPOS_INICIALES, StandardCharsets.UTF_8)) {
            if (!linea.strip().startsWith("--")) {
                sinComentarios.append(linea).append('\n');
            }
        }
        try (Connection conexion = esquema.conectar(); Statement sentencias = conexion.createStatement()) {
            if (charsetCliente != null) {
                sentencias.execute("SET NAMES " + charsetCliente);
            }
            for (String sentencia : sinComentarios.toString().split(";")) {
                if (!sentencia.isBlank()) {
                    String mayusculas = sentencia.toUpperCase();
                    assertFalse(mayusculas.matches("(?s).*\\b(CREATE|ALTER|DROP|TRUNCATE|DELETE)\\b.*"),
                            "El script de datos no debe modificar el esquema ni borrar datos");
                    sentencias.execute(sentencia);
                }
            }
        }
    }

    /** Controlador nuevo con DAO nuevos: cada operación abre su propia conexión. */
    private InventarioControlador controladorNuevo() {
        return new InventarioControlador(new InventarioService(
                new RepuestoDAO(esquema::conectar), new ProveedorDAO(esquema::conectar),
                ConsultaReservas.jdbc(esquema::conectar)));
    }

    private static String consola(InventarioControlador controlador, boolean proveedores, String... lineas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            Scanner entrada = new Scanner(String.join("\n", lineas));
            if (proveedores) {
                new VistaProveedores(entrada, salida, controlador).mostrar();
            } else {
                new VistaInventario(entrada, salida, controlador).mostrar();
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private int entero(String sql) throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next(), sql);
            return r.getInt(1);
        }
    }

    private String texto(String sql) throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next(), sql);
            return r.getString(1);
        }
    }

    private List<String> columnas(String tabla) throws SQLException {
        try (Connection c = esquema.conectar(); PreparedStatement s = c.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
            s.setString(1, tabla);
            try (ResultSet r = s.executeQuery()) {
                List<String> nombres = new ArrayList<>();
                while (r.next()) {
                    nombres.add(r.getString(1));
                }
                return nombres;
            }
        }
    }

    /** Huella del DDL instalado: tablas, columnas y tipos de la base desechable. */
    private String huellaDelEsquema() throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT GROUP_CONCAT(CONCAT(TABLE_NAME, '.', COLUMN_NAME, ':', COLUMN_TYPE) "
                        + "ORDER BY TABLE_NAME, ORDINAL_POSITION SEPARATOR ',') "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()")) {
            r.next();
            return r.getString(1);
        }
    }

    // ---------- pruebas ----------

    @Test
    void elEsquemaConsolidadoTieneExactamenteLasColumnasQueUsanLosDao() throws SQLException {
        assertEquals(List.of("id", "nombre"), columnas("repuesto_tipos"));
        assertEquals(List.of("id", "nombre", "persona_contacto", "telefono", "email"), columnas("proveedores"));
        assertEquals(List.of("id", "nombre", "marca", "repuesto_tipo_id", "precio_venta", "cantidad_stock",
                "stock_minimo"), columnas("repuestos"));
        assertEquals(List.of("servicio_id", "repuesto_id", "cantidad_reservada", "cantidad_usada",
                "precio_unitario_venta"), columnas("servicios_repuestos"));
        assertEquals(0, entero("SELECT COUNT(*) FROM repuesto_tipos"), "El esquema no trae datos de tipos");
    }

    @Test
    void inicializarLosTiposDeRepuestoEsRepetibleYNoCambiaElEsquema() throws Exception {
        String antes = huellaDelEsquema();

        inicializarTiposDeRepuesto();
        inicializarTiposDeRepuesto();

        assertEquals(TIPOS_PROPUESTOS, entero("SELECT COUNT(*) FROM repuesto_tipos"),
                "Ejecutarlo dos veces no duplica tipos");
        assertEquals(1, entero("SELECT COUNT(*) FROM repuesto_tipos WHERE nombre = 'Eléctricos'"),
                "Los acentos se guardan bien en utf8mb4");
        assertEquals(antes, huellaDelEsquema(), "El script de datos no modifica tablas ni columnas");
    }

    @Test
    void elScriptDeTiposGuardaBienLosAcentosAunqueElClienteUseOtraCodificacion() throws Exception {
        // El cliente mysql de muchas instalaciones usa latin1 por defecto.
        inicializarTiposDeRepuesto("latin1");
        // Repetirlo con un cliente utf8mb4 no debe crear duplicados.
        inicializarTiposDeRepuesto("utf8mb4");
        inicializarTiposDeRepuesto();

        assertEquals("456CC3A963747269636F73", texto("SELECT HEX(nombre) FROM repuesto_tipos WHERE id = 4"),
                "'Eléctricos' debe guardarse en UTF-8, sin doble codificación");
        assertEquals("Suspensión", texto("SELECT nombre FROM repuesto_tipos WHERE id = 5"));
        assertEquals(TIPOS_PROPUESTOS, entero("SELECT COUNT(*) FROM repuesto_tipos"),
                "Ejecutar el script con distintos clientes no duplica tipos");
    }

    @Test
    void flujoCompletoDesdeLaConsolaPersisteEntreConexiones() throws Exception {
        inicializarTiposDeRepuesto();
        int filtros = entero("SELECT id FROM repuesto_tipos WHERE nombre = 'Filtros'");

        // Sesión 1: registro de proveedor y de repuesto, entrada y salidas de stock.
        String proveedores = consola(controladorNuevo(), true,
                "1", "AutoPartes SAS", "Laura Gómez", "3001234567", "ventas@autopartes.co", "0");
        String inventario = consola(controladorNuevo(), false,
                "1", "Filtro de aceite", "Bosch", String.valueOf(filtros), "25000.00", "10", "3",
                "5", "1", "5",
                "6", "1", "12",
                "6", "1", "4",
                "0");

        assertTrue(proveedores.contains("Proveedor registrado con ID 1."), proveedores);
        assertTrue(inventario.contains("Repuesto registrado con ID 1."), inventario);
        assertTrue(inventario.contains("Entrada registrada. Stock actual: 15."));
        assertTrue(inventario.contains("Salida registrada. Stock actual: 3."));
        assertTrue(inventario.contains("Stock insuficiente: se pidieron 4 y hay 3 disponibles."));

        // Sesión 2: controlador y DAO nuevos, conexiones nuevas; los datos siguen ahí.
        String consulta = consola(controladorNuevo(), false, "2", "1", "4", "1", "3", "0");
        String proveedorGuardado = consola(controladorNuevo(), true, "3", "AutoPartes SAS", "4", "0");

        assertTrue(consulta.contains("Repuesto 1 | Filtro de aceite | Marca: Bosch | Tipo: " + filtros
                + " | Precio: 25000.00 | Stock: 3 | Mínimo: 3 | ¡Stock mínimo!"), consulta);
        assertTrue(consulta.contains("Stock disponible: 3 | Stock mínimo: 3"));
        assertTrue(proveedorGuardado.contains(
                "Proveedor 1 | AutoPartes SAS | Contacto: Laura Gómez | Teléfono: 3001234567 | Email: ventas@autopartes.co"),
                proveedorGuardado);

        // Verificación directa con SQL desde otra conexión, independiente de las vistas.
        assertEquals(3, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"));
        assertEquals(filtros, entero("SELECT repuesto_tipo_id FROM repuestos WHERE id = 1"));
        assertEquals(0, new BigDecimal("25000.00").compareTo(precio()));
        assertEquals(1, entero("SELECT COUNT(*) FROM proveedores WHERE nombre = 'AutoPartes SAS' "
                + "AND persona_contacto = 'Laura Gómez' AND email = 'ventas@autopartes.co'"));
        assertEquals(1, entero("SELECT COUNT(*) FROM repuestos"), "La salida rechazada no creó registros");
    }

    @Test
    void laOpcionOchoMuestraLoReservadoYUsadoDeUnaOrdenDesdeMySQL() throws Exception {
        inicializarTiposDeRepuesto();
        esquema.ejecutar("INSERT INTO repuestos (nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock, stock_minimo) "
                + "VALUES ('Filtro de aceite', 'Bosch', 1, 25000.00, 7, 3)");
        esquema.ejecutar("INSERT INTO clientes (nombre, documento_identidad, email) VALUES ('Ana', '1001', 'ana@correo.com')");
        esquema.ejecutar("INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) VALUES (1, 'ABC123', 'Mazda', '3', 2020)");
        esquema.ejecutar("INSERT INTO servicio_estados (nombre) VALUES ('Recibido')");
        esquema.ejecutar("INSERT INTO mantenimiento_tipos (nombre) VALUES ('Preventivo')");
        esquema.ejecutar("INSERT INTO servicios (vehiculo_id, mantenimiento_tipo_id, fecha_recepcion, descripcion_problema, estado_id) "
                + "VALUES (1, 1, '2026-11-20 08:00:00', 'Cambio de aceite', 1)");
        esquema.ejecutar("INSERT INTO servicios_repuestos (servicio_id, repuesto_id, cantidad_reservada, cantidad_usada, precio_unitario_venta) "
                + "VALUES (1, 1, 1, 2, 25000.00)");

        String salida = consola(controladorNuevo(), false, "8", "1", "8", "2", "0");

        assertTrue(salida.contains("Repuesto 1 | Filtro de aceite | Reservadas sin usar: 1 | Usadas: 2 | Precio unitario: 25000.00"), salida);
        assertTrue(salida.contains("La orden 2 no tiene repuestos reservados ni usados."));
    }

    @Test
    void sinInicializarLosTiposElRegistroDeRepuestoSeRechazaSinGuardar() throws SQLException {
        String salida = consola(controladorNuevo(), false,
                "1", "Filtro de aceite", "Bosch", "0");

        assertTrue(salida.contains("No hay tipos de repuesto registrados. Cárguelos con docs/inventario/repuesto_tipos_iniciales.sql"), salida);
        assertEquals(0, entero("SELECT COUNT(*) FROM repuestos"));
    }

    private BigDecimal precio() throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT precio_venta FROM repuestos WHERE id = 1")) {
            r.next();
            return r.getBigDecimal(1);
        }
    }
}
