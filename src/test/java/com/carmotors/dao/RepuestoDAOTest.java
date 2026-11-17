package com.carmotors.dao;

import com.carmotors.modelo.Repuesto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validaciones que el DAO hace antes de abrir una conexión a la base de datos. */
class RepuestoDAOTest {

    /** Falla si el DAO intenta conectarse: las validaciones deben ocurrir antes. */
    private final RepuestoDAO dao = new RepuestoDAO(() -> {
        throw new AssertionError("No debía abrir una conexión");
    });

    @Test
    void rechazaMovimientosConCantidadNoPositiva() {
        assertThrows(IllegalArgumentException.class, () -> dao.registrarEntrada(1, 0));
        assertThrows(IllegalArgumentException.class, () -> dao.registrarSalida(1, -3));
    }

    @Test
    void rechazaIdsNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> dao.buscarPorId(0));
        assertThrows(IllegalArgumentException.class, () -> dao.registrarEntrada(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> dao.registrarSalida(0, 5));
    }

    @Test
    void rechazaRegistrarUnRepuestoQueYaTieneId() {
        Repuesto existente = Repuesto.nuevo("Filtro", "Bosch", 1, BigDecimal.TEN, 1, 0).conId(4);

        assertThrows(IllegalArgumentException.class, () -> dao.registrar(existente));
        assertThrows(NullPointerException.class, () -> dao.registrar(null));
    }
}
