package com.carmotors.modelo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProveedorTest {

    @Test
    void creaUnProveedorNuevoSinId() {
        Proveedor proveedor = Proveedor.nuevo("AutoPartes SAS", "Laura Gómez", "3001234567", "ventas@autopartes.co");

        assertNull(proveedor.id());
        assertEquals("AutoPartes SAS", proveedor.nombre());
        assertEquals("ventas@autopartes.co", proveedor.email());
    }

    @Test
    void asignaElIdSinCambiarLosDemasDatos() {
        Proveedor guardado = Proveedor.nuevo("AutoPartes SAS", null, null, null).conId(3);

        assertEquals(3, guardado.id());
        assertEquals("AutoPartes SAS", guardado.nombre());
    }

    @Test
    void losDatosDeContactoSonOpcionalesYVaciosQuedanNulos() {
        Proveedor proveedor = Proveedor.nuevo("  Repuestos del Norte ", "  ", "", null);

        assertEquals("Repuestos del Norte", proveedor.nombre());
        assertNull(proveedor.personaContacto());
        assertNull(proveedor.telefono());
        assertNull(proveedor.email());
    }

    @Test
    void rechazaNombreVacio() {
        assertThrows(IllegalArgumentException.class, () -> Proveedor.nuevo(" ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Proveedor.nuevo(null, null, null, null));
    }

    @Test
    void rechazaEmailInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> Proveedor.nuevo("AutoPartes SAS", null, null, "sin-arroba.com"));
    }

    @Test
    void rechazaTextosMasLargosQueLaColumna() {
        assertThrows(IllegalArgumentException.class,
                () -> Proveedor.nuevo("x".repeat(256), null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Proveedor.nuevo("AutoPartes SAS", "x".repeat(121), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> Proveedor.nuevo("AutoPartes SAS", null, "1".repeat(21), null));
    }

    @Test
    void rechazaIdNoPositivo() {
        assertThrows(IllegalArgumentException.class,
                () -> Proveedor.nuevo("AutoPartes SAS", null, null, null).conId(0));
    }
}
