package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vistas de Inventario y Proveedores manejadas por consola contra MySQL real
 * (Vista → Controlador → Service → DAO → tablas de {@code inventario.sql}).
 * Usa una base desechable propia y solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class VistasInventarioIntegracionTest {

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

    private String consola(boolean proveedores, String... lineas) {
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
            r.next();
            return r.getInt(1);
        }
    }

    @Test
    void inventarioDesdeLaConsolaContraMySQL() throws SQLException {
        String salida = consola(false,
                "1", "Filtro de aceite", "Bosch", "1", "25000.00", "10", "3",
                "1", "Bujía", "NGK", "99",
                "5", "1", "5",
                "6", "1", "12",
                "6", "1", "4",
                "4", "1", "7", "3", "0");

        assertTrue(salida.contains("Repuesto registrado con ID 1."), salida);
        assertTrue(salida.contains("El tipo de repuesto 99 no existe. Elija uno de la lista."), "Un tipo inexistente se rechaza antes de guardar");
        assertTrue(salida.contains("Tipos disponibles: 1 = Filtros"), "Se muestran los tipos antes de pedir el ID");
        assertTrue(salida.contains("Entrada registrada. Stock actual: 15."));
        assertTrue(salida.contains("Salida registrada. Stock actual: 3."));
        assertTrue(salida.contains("Stock insuficiente: se pidieron 4 y hay 3 disponibles."));
        assertTrue(salida.contains("Stock disponible: 3 | Stock mínimo: 3"));
        assertTrue(salida.contains("Alerta: el repuesto está en su stock mínimo"));
        assertEquals(3, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"));
        assertEquals(1, entero("SELECT COUNT(*) FROM repuestos"), "El repuesto con tipo inexistente no se guardó");
    }

    @Test
    void proveedoresDesdeLaConsolaContraMySQL() throws SQLException {
        String salida = consola(true,
                "1", "AutoPartes SAS", "Laura", "3001234567", "ventas@ap.co",
                "1", "autopartes sas", "", "", "",
                "3", "AutoPartes SAS",
                "5", "1", "Andrés", "-", "",
                "2", "1", "4", "0");

        assertTrue(salida.contains("Proveedor registrado con ID 1."), salida);
        assertTrue(salida.contains("Error: Ya existe un proveedor con el nombre autopartes sas"),
                "La búsqueda por nombre no distingue mayúsculas, igual que la columna UNIQUE");
        assertTrue(salida.contains("Contacto del proveedor actualizado."));
        assertTrue(salida.contains(
                "Proveedor 1 | AutoPartes SAS | Contacto: Andrés | Teléfono: sin registrar | Email: ventas@ap.co"));
        assertEquals(1, entero("SELECT COUNT(*) FROM proveedores"));
        assertEquals(1, entero("SELECT COUNT(*) FROM proveedores WHERE telefono IS NULL AND persona_contacto = 'Andrés'"));
    }
}
