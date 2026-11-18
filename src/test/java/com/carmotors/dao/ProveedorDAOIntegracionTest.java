package com.carmotors.dao;

import com.carmotors.modelo.Proveedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba {@link ProveedorDAO} contra un MySQL real de <b>prueba</b>.
 * Solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL}; ver {@link EsquemaInventarioPrueba}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class ProveedorDAOIntegracionTest {

    private ProveedorDAO dao;

    @BeforeEach
    void recrearTablasDeInventario() throws SQLException, IOException {
        EsquemaInventarioPrueba.recrear();
        dao = new ProveedorDAO(EsquemaInventarioPrueba::conectar);
    }

    @Test
    void registraYBuscaPorIdYPorNombre() throws SQLException {
        Proveedor guardado = dao.registrar(
                Proveedor.nuevo("AutoPartes SAS", "Laura Gómez", "3001234567", "ventas@ap.co"));

        assertEquals(Optional.of(guardado), dao.buscarPorId(guardado.id()));
        assertEquals(Optional.of(guardado), dao.buscarPorNombre("AutoPartes SAS"));
    }

    @Test
    void guardaLosContactosVaciosComoNull() throws SQLException {
        Proveedor guardado = dao.registrar(Proveedor.nuevo("Repuestos del Norte", "", null, " "));

        Proveedor leido = dao.buscarPorId(guardado.id()).orElseThrow();
        assertNull(leido.personaContacto());
        assertNull(leido.email());
    }

    @Test
    void buscarInexistenteDevuelveVacio() throws SQLException {
        assertTrue(dao.buscarPorId(999).isEmpty());
        assertTrue(dao.buscarPorNombre("No existe").isEmpty());
    }

    @Test
    void listaEnOrdenDeId() throws SQLException {
        Proveedor primero = dao.registrar(Proveedor.nuevo("AutoPartes SAS", null, null, null));
        Proveedor segundo = dao.registrar(Proveedor.nuevo("Repuestos del Norte", null, null, null));

        assertEquals(List.of(primero, segundo), dao.listar());
    }

    @Test
    void laBaseRechazaNombresRepetidos() throws SQLException {
        dao.registrar(Proveedor.nuevo("AutoPartes SAS", null, null, null));

        assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> dao.registrar(Proveedor.nuevo("AutoPartes SAS", null, null, null)));
    }

    @Test
    void actualizaElContacto() throws SQLException {
        Proveedor guardado = dao.registrar(Proveedor.nuevo("AutoPartes SAS", "Laura", null, null));

        assertTrue(dao.actualizarContacto(
                new Proveedor(guardado.id(), guardado.nombre(), "Andrés", "6011234", "andres@ap.co")));

        Proveedor leido = dao.buscarPorId(guardado.id()).orElseThrow();
        assertEquals("Andrés", leido.personaContacto());
        assertEquals("6011234", leido.telefono());
        assertEquals("andres@ap.co", leido.email());
    }

    @Test
    void actualizarUnProveedorInexistenteDevuelveFalse() throws SQLException {
        assertFalse(dao.actualizarContacto(new Proveedor(999, "Fantasma", null, null, null)));
    }
}
