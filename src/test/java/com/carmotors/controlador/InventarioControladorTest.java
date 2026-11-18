package com.carmotors.controlador;

import com.carmotors.dao.ProveedoresEnMemoria;
import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.RepuestosEnMemoria;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba el controlador de Inventario con DAO en memoria, sin base de datos. */
class InventarioControladorTest {

    private final RepuestosEnMemoria repuestos = new RepuestosEnMemoria();
    private final ProveedoresEnMemoria proveedores = new ProveedoresEnMemoria();
    private final InventarioControlador controlador =
            new InventarioControlador(new InventarioService(repuestos, proveedores));

    private Repuesto filtro(int stock, int minimo) throws Exception {
        return controlador.registrarRepuesto("Filtro de aceite", "Bosch", 1,
                new BigDecimal("25000.00"), stock, minimo);
    }

    @Test
    void exigeUnServicio() {
        assertThrows(NullPointerException.class, () -> new InventarioControlador(null));
    }

    // ---------- repuestos ----------

    @Test
    void registraYConsultaRepuestos() throws Exception {
        Repuesto guardado = filtro(10, 3);

        assertEquals(Optional.of(guardado), controlador.buscarRepuesto(guardado.id()));
        assertEquals(List.of(guardado), controlador.listarRepuestos());
        assertTrue(controlador.buscarRepuesto(99).isEmpty());
    }

    @Test
    void rechazaRepuestosConDatosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarRepuesto(
                "Filtro", "Bosch", 1, new BigDecimal("1.00"), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarRepuesto(
                " ", "Bosch", 1, new BigDecimal("1.00"), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarRepuesto(
                "Filtro", "Bosch", 1, null, 1, 0));
        assertEquals(0, repuestos.cantidadGuardados());
    }

    @Test
    void listaLosRepuestosEnStockMinimo() throws Exception {
        filtro(10, 3);
        Repuesto bajo = filtro(2, 3);

        assertEquals(List.of(bajo), controlador.listarRepuestosEnStockMinimo());
    }

    // ---------- movimientos de stock ----------

    @Test
    void registraEntradasYSalidas() throws Exception {
        Repuesto repuesto = filtro(10, 3);

        assertEquals(15, controlador.registrarEntrada(repuesto.id(), 5));
        assertEquals(9, controlador.registrarSalida(repuesto.id(), 6));
        assertEquals(9, repuestos.stock(repuesto.id()));
    }

    @Test
    void rechazaCantidadesInvalidas() throws Exception {
        Repuesto repuesto = filtro(10, 3);

        assertThrows(IllegalArgumentException.class, () -> controlador.registrarEntrada(repuesto.id(), 0));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarEntrada(repuesto.id(), -4));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarSalida(repuesto.id(), 0));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarSalida(repuesto.id(), -1));
        assertThrows(IllegalArgumentException.class, () -> controlador.hayStockSuficiente(repuesto.id(), 0));
        assertEquals(10, repuestos.stock(repuesto.id()));
        assertEquals(0, repuestos.movimientos());
    }

    @Test
    void rechazaUnaSalidaConStockInsuficienteSinCambiarNada() throws Exception {
        Repuesto repuesto = filtro(4, 1);

        StockInsuficienteException error = assertThrows(StockInsuficienteException.class,
                () -> controlador.registrarSalida(repuesto.id(), 5));

        assertEquals(4, error.getDisponible());
        assertEquals(5, error.getSolicitado());
        assertEquals(4, repuestos.stock(repuesto.id()));
        assertFalse(controlador.hayStockSuficiente(repuesto.id(), 5));
        assertTrue(controlador.hayStockSuficiente(repuesto.id(), 4));
    }

    @Test
    void rechazaMovimientosDeRepuestosInexistentes() {
        assertThrows(RepuestoNoEncontradoException.class, () -> controlador.registrarEntrada(99, 1));
        assertThrows(RepuestoNoEncontradoException.class, () -> controlador.registrarSalida(99, 1));
    }

    // ---------- proveedores ----------

    @Test
    void registraConsultaYActualizaProveedores() throws Exception {
        Proveedor guardado = controlador.registrarProveedor("AutoPartes SAS", "Laura", null, "ventas@ap.co");

        assertEquals(Optional.of(guardado), controlador.buscarProveedor(guardado.id()));
        assertEquals(List.of(guardado), controlador.listarProveedores());
        assertTrue(controlador.actualizarContactoProveedor(guardado.id(), "Andrés", "6011234", null));
        assertEquals("Andrés", controlador.buscarProveedor(guardado.id()).orElseThrow().personaContacto());
        assertFalse(controlador.actualizarContactoProveedor(99, "Nadie", null, null));
    }

    @Test
    void rechazaProveedoresDuplicados() throws Exception {
        controlador.registrarProveedor("AutoPartes SAS", null, null, null);

        assertThrows(IllegalStateException.class,
                () -> controlador.registrarProveedor("AutoPartes SAS", "Otra persona", null, null));
        assertThrows(IllegalStateException.class,
                () -> controlador.registrarProveedor("  autopartes sas ", null, null, null));
        assertEquals(1, proveedores.cantidadGuardados());
    }

    @Test
    void rechazaProveedoresConDatosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarProveedor(" ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarProveedor("AutoPartes SAS", null, null, "sin-arroba"));
        assertThrows(IllegalArgumentException.class, () -> controlador.buscarProveedor(0));
        assertEquals(0, proveedores.cantidadGuardados());
    }
}
