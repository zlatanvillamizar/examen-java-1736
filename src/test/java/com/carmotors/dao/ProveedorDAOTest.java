package com.carmotors.dao;

import com.carmotors.modelo.Proveedor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validaciones que el DAO hace antes de abrir una conexión a la base de datos. */
class ProveedorDAOTest {

    /** Falla si el DAO intenta conectarse: las validaciones deben ocurrir antes. */
    private final ProveedorDAO dao = new ProveedorDAO(() -> {
        throw new AssertionError("No debía abrir una conexión");
    });

    @Test
    void rechazaIdsNoPositivosYNombresVacios() {
        assertThrows(IllegalArgumentException.class, () -> dao.buscarPorId(0));
        assertThrows(IllegalArgumentException.class, () -> dao.buscarPorNombre("  "));
        assertThrows(IllegalArgumentException.class, () -> dao.buscarPorNombre(null));
    }

    @Test
    void rechazaRegistrarUnProveedorQueYaTieneId() {
        Proveedor existente = Proveedor.nuevo("AutoPartes SAS", null, null, null).conId(4);

        assertThrows(IllegalArgumentException.class, () -> dao.registrar(existente));
        assertThrows(NullPointerException.class, () -> dao.registrar(null));
    }

    @Test
    void rechazaActualizarUnProveedorSinId() {
        Proveedor sinId = Proveedor.nuevo("AutoPartes SAS", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> dao.actualizarContacto(sinId));
        assertThrows(NullPointerException.class, () -> dao.actualizarContacto(null));
    }
}
