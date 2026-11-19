package com.carmotors.modelo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DetalleFacturaTest {

    @Test
    void subtotal_multiplicaCantidadPorPrecio() {
        DetalleFactura d = new DetalleFactura(1, "Filtro", 3, new BigDecimal("10.50"));
        assertEquals(new BigDecimal("31.50"), d.subtotal());
    }

    @Test
    void repuestoInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetalleFactura(0, "x", 1, BigDecimal.ONE));
    }

    @Test
    void cantidadCeroONegativa_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetalleFactura(1, "x", 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new DetalleFactura(1, "x", -2, BigDecimal.ONE));
    }

    @Test
    void precioNuloONegativo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetalleFactura(1, "x", 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DetalleFactura(1, "x", 1, new BigDecimal("-1")));
    }
}