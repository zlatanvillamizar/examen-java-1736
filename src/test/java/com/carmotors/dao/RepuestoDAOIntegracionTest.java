package com.carmotors.dao;

import com.carmotors.modelo.Repuesto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba {@link RepuestoDAO} contra un MySQL real de <b>prueba</b>.
 *
 * <p>Solo se ejecuta si existe la variable {@code CARMOTORS_TEST_DB_URL}, por
 * ejemplo {@code jdbc:mysql://localhost:3306/carmotors_pruebas}. Usuario y
 * contraseña se leen de {@code CARMOTORS_TEST_DB_USER} y
 * {@code CARMOTORS_TEST_DB_PASSWORD}. <b>Borra y recrea las tablas de
 * inventario</b>: nunca la apuntes a la base real.</p>
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class RepuestoDAOIntegracionTest {

    private static final Path SCRIPT_INVENTARIO = Path.of("database", "bloques", "inventario.sql");

    private RepuestoDAO dao;

    private static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                System.getenv("CARMOTORS_TEST_DB_URL"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
    }

    @BeforeEach
    void recrearTablasDeInventario() throws SQLException, IOException {
        String script = Files.readString(SCRIPT_INVENTARIO, StandardCharsets.UTF_8);
        try (Connection conexion = conectar(); Statement sentencia = conexion.createStatement()) {
            String base = conexion.getCatalog();
            if (base == null || !base.contains("prueba")) {
                throw new IllegalStateException(
                        "Por seguridad, la base de prueba debe tener 'prueba' en el nombre: " + base);
            }
            sentencia.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String tabla : List.of("evaluaciones_proveedor", "proveedores_repuestos", "lotes",
                    "repuestos", "proveedores", "repuesto_tipos")) {
                sentencia.execute("DROP TABLE IF EXISTS " + tabla);
            }
            sentencia.execute("SET FOREIGN_KEY_CHECKS = 1");
            for (String instruccion : instrucciones(script)) {
                sentencia.execute(instruccion);
            }
            sentencia.execute("INSERT INTO repuesto_tipos (nombre) VALUES ('Filtros')");
        }
        dao = new RepuestoDAO(RepuestoDAOIntegracionTest::conectar);
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

    private Repuesto guardar(String nombre, int stock) throws SQLException {
        return dao.registrar(Repuesto.nuevo(nombre, "Bosch", 1, new BigDecimal("25000.00"), stock, 2));
    }

    @Test
    void registraYBuscaPorId() throws SQLException {
        Repuesto guardado = guardar("Filtro de aceite", 10);

        Optional<Repuesto> encontrado = dao.buscarPorId(guardado.id());

        assertEquals(Optional.of(guardado), encontrado);
    }

    @Test
    void buscarUnIdInexistenteDevuelveVacio() throws SQLException {
        assertTrue(dao.buscarPorId(999).isEmpty());
    }

    @Test
    void listaEnOrdenDeId() throws SQLException {
        Repuesto primero = guardar("Filtro de aceite", 10);
        Repuesto segundo = guardar("Bujía", 4);

        assertEquals(List.of(primero, segundo), dao.listar());
    }

    @Test
    void laEntradaSumaAlStock() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 10);

        assertEquals(15, dao.registrarEntrada(repuesto.id(), 5));
        assertEquals(15, dao.buscarPorId(repuesto.id()).orElseThrow().cantidadStock());
    }

    @Test
    void laSalidaRestaDelStockHastaCero() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 10);

        assertEquals(4, dao.registrarSalida(repuesto.id(), 6));
        assertEquals(0, dao.registrarSalida(repuesto.id(), 4));
    }

    @Test
    void laSalidaNoDejaElStockNegativoYNoCambiaNada() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 3);

        StockInsuficienteException error =
                assertThrows(StockInsuficienteException.class, () -> dao.registrarSalida(repuesto.id(), 5));

        assertEquals(3, error.getDisponible());
        assertEquals(5, error.getSolicitado());
        assertEquals(3, dao.buscarPorId(repuesto.id()).orElseThrow().cantidadStock());
    }

    @Test
    void losMovimientosSobreUnRepuestoInexistenteFallan() {
        assertThrows(RepuestoNoEncontradoException.class, () -> dao.registrarEntrada(999, 1));
        assertThrows(RepuestoNoEncontradoException.class, () -> dao.registrarSalida(999, 1));
    }

    @Test
    void noRegistraUnRepuestoConTipoInexistente() {
        Repuesto conTipoInexistente =
                Repuesto.nuevo("Filtro", "Bosch", 99, new BigDecimal("1.00"), 1, 0);

        assertThrows(SQLException.class, () -> dao.registrar(conTipoInexistente));
    }
}
