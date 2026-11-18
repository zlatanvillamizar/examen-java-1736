package com.carmotors.servicio.taller.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentificadoresTest {

    @Test
    void servicioAceptaIdentificadoresPositivos() {
        assertEquals(1L, new IdentificadorServicio(1).valor());
        assertEquals(123L, new IdentificadorServicio(123).valor());
    }

    @Test
    void servicioRechazaIdentificadoresNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new IdentificadorServicio(0));
        assertThrows(IllegalArgumentException.class, () -> new IdentificadorServicio(-1));
    }

    @Test
    void vehiculoAceptaIdentificadoresPositivos() {
        assertEquals(7L, new IdentificadorVehiculo(7).valor());
    }

    @Test
    void vehiculoRechazaIdentificadoresNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new IdentificadorVehiculo(0));
        assertThrows(IllegalArgumentException.class, () -> new IdentificadorVehiculo(-42));
    }

    @Test
    void identificadoresTienenIgualdadPorValor() {
        assertEquals(new IdentificadorServicio(3), new IdentificadorServicio(3));
        assertNotEquals(new IdentificadorServicio(3), new IdentificadorServicio(4));
    }
}
