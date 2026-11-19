package com.carmotors.servicio.taller.integracion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContratoInventarioServiciosTest {

    @Test
    void aceptaUnaReservaValida() {
        ReservaRepuesto reserva = new ReservaRepuesto(10, 4, 3);

        assertEquals(10, reserva.servicioId());
        assertEquals(4, reserva.repuestoId());
        assertEquals(3, reserva.cantidad());
    }

    @Test
    void rechazaReservasSinCantidadPositiva() {
        assertThrows(IllegalArgumentException.class, () -> new ReservaRepuesto(1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReservaRepuesto(1, 2, -1));
    }

    @Test
    void rechazaReservasConIdentificadoresInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new ReservaRepuesto(0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new ReservaRepuesto(1, 0, 1));
    }

    @Test
    void aceptaUnConsumoValido() {
        ConsumoRepuesto consumo = new ConsumoRepuesto(8, 6, 2);

        assertEquals(8, consumo.servicioId());
        assertEquals(6, consumo.repuestoId());
        assertEquals(2, consumo.cantidad());
    }

    @Test
    void rechazaConsumosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new ConsumoRepuesto(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConsumoRepuesto(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConsumoRepuesto(1, 1, 0));
    }
}
