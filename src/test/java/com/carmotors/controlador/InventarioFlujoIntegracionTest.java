package com.carmotors.controlador;

import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujo completo de Inventario contra MySQL real:
 * Controlador → Service → DAO → tablas de {@code inventario.sql}.
 * Usa una base desechable propia (ver {@link EsquemaInventarioPrueba}) y solo se
 * ejecuta si existe {@code CARMOTORS_TEST_DB_URL}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class InventarioFlujoIntegracionTest {

    private EsquemaInventarioPrueba esquema;
    private InventarioControlador controlador;

    @BeforeEach
    void crearBaseDesechable() throws SQLException, IOException {
        esquema = EsquemaInventarioPrueba.crear();
        esquema.ejecutar("INSERT INTO repuesto_tipos (nombre) VALUES ('Filtros')");
        controlador = new InventarioControlador(new InventarioService(
                new RepuestoDAO(esquema::conectar), new ProveedorDAO(esquema::conectar)));
    }

    @AfterEach
    void eliminarBaseDesechable() throws SQLException {
        if (esquema != null) {
            esquema.close();
        }
    }

    @Test
    void flujoDeRepuestosYStockContraMySQL() throws SQLException {
        Repuesto filtro = controlador.registrarRepuesto("Filtro de aceite", "Bosch", 1,
                new BigDecimal("25000.00"), 10, 3);

        assertEquals(filtro, controlador.buscarRepuesto(filtro.id()).orElseThrow());
        assertEquals(List.of(filtro), controlador.listarRepuestos());

        assertEquals(15, controlador.registrarEntrada(filtro.id(), 5));
        assertEquals(3, controlador.registrarSalida(filtro.id(), 12));
        assertEquals(List.of(3), controlador.listarRepuestosEnStockMinimo().stream()
                .map(Repuesto::cantidadStock).toList());

        assertThrows(StockInsuficienteException.class, () -> controlador.registrarSalida(filtro.id(), 4));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarEntrada(filtro.id(), 0));
        assertEquals(3, controlador.buscarRepuesto(filtro.id()).orElseThrow().cantidadStock());
        assertTrue(controlador.hayStockSuficiente(filtro.id(), 3));
        assertFalse(controlador.hayStockSuficiente(filtro.id(), 4));
    }

    @Test
    void unRepuestoConTipoInexistenteLoRechazaLaBaseDeDatos() {
        assertThrows(SQLException.class, () -> controlador.registrarRepuesto(
                "Bujía", "NGK", 99, new BigDecimal("12000.00"), 1, 0));
    }

    @Test
    void flujoDeProveedoresContraMySQL() throws SQLException {
        Proveedor proveedor = controlador.registrarProveedor("AutoPartes SAS", "Laura", "3001234567", "ventas@ap.co");

        assertEquals(proveedor, controlador.buscarProveedor(proveedor.id()).orElseThrow());
        assertEquals(List.of(proveedor), controlador.listarProveedores());

        // La columna usa utf8mb4_unicode_ci: el duplicado se detecta sin importar mayúsculas.
        assertThrows(IllegalStateException.class,
                () -> controlador.registrarProveedor("autopartes sas", null, null, null));

        assertTrue(controlador.actualizarContactoProveedor(proveedor.id(), "Andrés", null, "andres@ap.co"));
        Proveedor actualizado = controlador.buscarProveedor(proveedor.id()).orElseThrow();
        assertEquals("Andrés", actualizado.personaContacto());
        assertEquals("andres@ap.co", actualizado.email());
        assertEquals(1, controlador.listarProveedores().size());
    }
}
