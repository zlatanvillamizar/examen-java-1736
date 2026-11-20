package com.carmotors.servicios.inventario;

import com.carmotors.dao.ProveedoresEnMemoria;
import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.RepuestosEnMemoria;
import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de inventario probadas con DAO en memoria, sin base de datos. */
class InventarioServiceTest {

    private final RepuestosEnMemoria repuestos = new RepuestosEnMemoria();
    private final ProveedoresEnMemoria proveedores = new ProveedoresEnMemoria();
    private final InventarioService inventario = new InventarioService(repuestos, proveedores);

    private Repuesto conStock(int stock, int minimo) throws Exception {
        return inventario.registrarRepuesto("Filtro de aceite", "Bosch", 1,
                new BigDecimal("25000.00"), stock, minimo);
    }

    // ---------- repuestos y stock ----------

    @Test
    void registraYBuscaUnRepuesto() throws Exception {
        Repuesto guardado = conStock(10, 3);

        assertEquals(Optional.of(guardado), inventario.buscarRepuesto(guardado.id()));
        assertEquals(List.of(guardado), inventario.listarRepuestos());
    }

    @Test
    void laEntradaSumaAlStock() throws Exception {
        Repuesto repuesto = conStock(10, 3);

        assertEquals(15, inventario.registrarEntrada(repuesto.id(), 5));
        assertEquals(15, repuestos.stock(repuesto.id()));
    }

    @Test
    void laSalidaRestaDelStockHastaCero() throws Exception {
        Repuesto repuesto = conStock(10, 3);

        assertEquals(4, inventario.registrarSalida(repuesto.id(), 6));
        assertEquals(0, inventario.registrarSalida(repuesto.id(), 4));
    }

    @Test
    void laSalidaMayorAlStockSeRechazaSinCambiarNada() throws Exception {
        Repuesto repuesto = conStock(3, 1);

        StockInsuficienteException error = assertThrows(StockInsuficienteException.class,
                () -> inventario.registrarSalida(repuesto.id(), 5));

        assertEquals(3, error.getDisponible());
        assertEquals(5, error.getSolicitado());
        assertEquals(3, repuestos.stock(repuesto.id()));
        assertEquals(0, repuestos.movimientos(), "No debía llegar a la base de datos");
    }

    @Test
    void rechazaMovimientosConCantidadNoPositiva() throws Exception {
        Repuesto repuesto = conStock(10, 3);

        assertThrows(IllegalArgumentException.class, () -> inventario.registrarEntrada(repuesto.id(), 0));
        assertThrows(IllegalArgumentException.class, () -> inventario.registrarSalida(repuesto.id(), -2));
        assertEquals(10, repuestos.stock(repuesto.id()));
    }

    @Test
    void rechazaMovimientosDeUnRepuestoInexistente() {
        assertThrows(RepuestoNoEncontradoException.class, () -> inventario.registrarEntrada(99, 1));
        assertThrows(RepuestoNoEncontradoException.class, () -> inventario.registrarSalida(99, 1));
        assertThrows(RepuestoNoEncontradoException.class, () -> inventario.hayStockSuficiente(99, 1));
    }

    @Test
    void rechazaUnaEntradaQueDesbordariaElStock() throws Exception {
        Repuesto repuesto = conStock(Integer.MAX_VALUE - 1, 0);

        assertThrows(IllegalArgumentException.class, () -> inventario.registrarEntrada(repuesto.id(), 2));
        assertEquals(Integer.MAX_VALUE - 1, repuestos.stock(repuesto.id()));
    }

    @Test
    void rechazaIdsNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> inventario.buscarRepuesto(0));
        assertThrows(IllegalArgumentException.class, () -> inventario.registrarEntrada(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> inventario.buscarProveedor(0));
    }

    @Test
    void indicaSiHayStockSuficiente() throws Exception {
        Repuesto repuesto = conStock(5, 1);

        assertTrue(inventario.hayStockSuficiente(repuesto.id(), 5));
        assertFalse(inventario.hayStockSuficiente(repuesto.id(), 6));
    }

    @Test
    void listaLosRepuestosEnStockMinimo() throws Exception {
        Repuesto suficiente = conStock(10, 3);
        Repuesto enMinimo = conStock(3, 3);
        Repuesto debajo = conStock(1, 2);

        assertEquals(List.of(enMinimo, debajo), inventario.listarRepuestosEnStockMinimo());
        inventario.registrarSalida(suficiente.id(), 7);
        assertEquals(3, inventario.listarRepuestosEnStockMinimo().size());
    }

    @Test
    void noRegistraRepuestosConDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> inventario.registrarRepuesto(
                "Filtro", "Bosch", 1, new BigDecimal("1.00"), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> inventario.registrarRepuesto(
                "Filtro", "Bosch", 1, new BigDecimal("-1.00"), 1, 0));
        assertEquals(0, repuestos.cantidadGuardados());
    }

    // ---------- proveedores ----------

    @Test
    void registraYListaProveedores() throws Exception {
        Proveedor guardado = inventario.registrarProveedor("AutoPartes SAS", "Laura", "3001234567", "ventas@ap.co");

        assertEquals(Optional.of(guardado), inventario.buscarProveedor(guardado.id()));
        assertEquals(List.of(guardado), inventario.listarProveedores());
    }

    @Test
    void buscaUnProveedorPorNombreYValidaElNombre() throws Exception {
        Proveedor guardado = inventario.registrarProveedor("AutoPartes SAS", "Laura", null, null);

        assertEquals(Optional.of(guardado), inventario.buscarProveedorPorNombre("  AutoPartes SAS "));
        assertTrue(inventario.buscarProveedorPorNombre("Otro").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> inventario.buscarProveedorPorNombre(" "));
        assertThrows(IllegalArgumentException.class, () -> inventario.buscarProveedorPorNombre(null));
    }

    @Test
    void listaLosRepuestosDeUnaOrdenYValidaElId() throws Exception {
        ConsultaReservas consulta = servicioId -> List.of(
                new ReservaRepuestoDAO.Linea(servicioId, 1, 1, 2, new BigDecimal("25000.00")));
        InventarioService conReservas = new InventarioService(repuestos, proveedores, consulta);

        assertEquals(1, conReservas.listarRepuestosDeOrden(3).size());
        assertEquals(3, conReservas.listarRepuestosDeOrden(3).get(0).servicioId());
        assertThrows(IllegalArgumentException.class, () -> conReservas.listarRepuestosDeOrden(0));
        assertThrows(NullPointerException.class, () -> new InventarioService(repuestos, proveedores, null));
    }

    @Test
    void listaLosTiposDeRepuesto() throws Exception {
        assertEquals(java.util.Map.of(1, "Filtros"), inventario.listarTiposDeRepuesto());
    }

    @Test
    void noPermiteDosProveedoresConElMismoNombre() throws Exception {
        inventario.registrarProveedor("AutoPartes SAS", null, null, null);

        assertThrows(IllegalStateException.class,
                () -> inventario.registrarProveedor("  AutoPartes SAS ", null, null, null));
        assertEquals(1, proveedores.cantidadGuardados());
    }

    @Test
    void actualizaElContactoDeUnProveedorExistente() throws Exception {
        Proveedor guardado = inventario.registrarProveedor("AutoPartes SAS", "Laura", null, null);

        assertTrue(inventario.actualizarContactoProveedor(guardado.id(), "Andrés", "6011234", "andres@ap.co"));
        Proveedor actualizado = inventario.buscarProveedor(guardado.id()).orElseThrow();
        assertEquals("AutoPartes SAS", actualizado.nombre());
        assertEquals("Andrés", actualizado.personaContacto());
        assertEquals("andres@ap.co", actualizado.email());
    }

    @Test
    void actualizarUnProveedorInexistenteDevuelveFalse() throws Exception {
        assertFalse(inventario.actualizarContactoProveedor(42, "Andrés", null, null));
    }

    @Test
    void noActualizaConUnEmailInvalido() throws Exception {
        Proveedor guardado = inventario.registrarProveedor("AutoPartes SAS", null, null, "ok@ap.co");

        assertThrows(IllegalArgumentException.class,
                () -> inventario.actualizarContactoProveedor(guardado.id(), null, null, "no-es-email"));
        assertEquals("ok@ap.co", inventario.buscarProveedor(guardado.id()).orElseThrow().email());
    }
}
