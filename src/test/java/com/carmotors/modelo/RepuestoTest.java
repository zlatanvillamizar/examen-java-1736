package com.carmotors.modelo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepuestoTest {

    private static Repuesto conStock(int stock, int minimo) {
        return Repuesto.nuevo("Filtro de aceite", "Bosch", 1, new BigDecimal("25000.00"), stock, minimo);
    }

    @Test
    void creaUnRepuestoNuevoSinId() {
        Repuesto repuesto = conStock(10, 3);

        assertNull(repuesto.id());
        assertEquals("Filtro de aceite", repuesto.nombre());
        assertEquals(new BigDecimal("25000.00"), repuesto.precioVenta());
    }

    @Test
    void asignaElIdSinCambiarLosDemasDatos() {
        Repuesto guardado = conStock(10, 3).conId(7);

        assertEquals(7, guardado.id());
        assertEquals(10, guardado.cantidadStock());
    }

    @Test
    void eliminaEspaciosSobrantesEnTextos() {
        Repuesto repuesto = Repuesto.nuevo("  Pastillas  ", " Brembo ", 1, BigDecimal.ONE, 0, 0);

        assertEquals("Pastillas", repuesto.nombre());
        assertEquals("Brembo", repuesto.marca());
    }

    @Test
    void rechazaStockNegativo() {
        assertThrows(IllegalArgumentException.class, () -> conStock(-1, 0));
    }

    @Test
    void rechazaStockMinimoNegativo() {
        assertThrows(IllegalArgumentException.class, () -> conStock(5, -1));
    }

    @Test
    void rechazaPrecioNegativoONulo() {
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "Bosch", 1, new BigDecimal("-0.01"), 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "Bosch", 1, null, 1, 0));
    }

    @Test
    void rechazaPrecioQueNoCabeEnDecimal10Coma2() {
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "Bosch", 1, new BigDecimal("1.234"), 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "Bosch", 1, new BigDecimal("123456789.00"), 1, 0));
    }

    @Test
    void rechazaNombreOMarcaVaciosODemasiadoLargos() {
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo(" ", "Bosch", 1, BigDecimal.ONE, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", null, 1, BigDecimal.ONE, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "x".repeat(101), 1, BigDecimal.ONE, 1, 0));
    }

    @Test
    void rechazaIdsNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> conStock(1, 0).conId(0));
        assertThrows(IllegalArgumentException.class,
                () -> Repuesto.nuevo("Filtro", "Bosch", 0, BigDecimal.ONE, 1, 0));
    }

    @Test
    void indicaCuandoElStockLlegaAlMinimo() {
        assertTrue(conStock(3, 3).estaEnStockMinimo());
        assertTrue(conStock(2, 3).estaEnStockMinimo());
        assertFalse(conStock(4, 3).estaEnStockMinimo());
    }
}
