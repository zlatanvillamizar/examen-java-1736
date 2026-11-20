package com.carmotors.controlador;

import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;
import com.carmotors.servicios.inventario.InventarioService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Punto de entrada del módulo de Inventario para las vistas de consola
 * ({@code VistaInventario} y {@code VistaProveedores}).
 * No contiene SQL: delega las reglas de negocio en {@link InventarioService}.
 *
 * <p>Errores que la vista debe manejar:</p>
 * <ul>
 *   <li>{@link IllegalArgumentException}: datos inválidos (cantidades no positivas, ids, textos, email).</li>
 *   <li>{@link com.carmotors.dao.StockInsuficienteException}: la salida dejaría el stock en negativo.</li>
 *   <li>{@link com.carmotors.dao.RepuestoNoEncontradoException}: el repuesto no existe.</li>
 *   <li>{@link IllegalStateException}: ya existe un proveedor con ese nombre.</li>
 * </ul>
 *
 * <p>Las reservas de repuestos para órdenes de servicio no pasan por este controlador:
 * Servicios las coordina en su propia transacción con
 * {@link com.carmotors.servicios.inventario.InventarioServiciosJdbc}.</p>
 */
public final class InventarioControlador {

    private final InventarioService inventario;

    public InventarioControlador() {
        this(new InventarioService());
    }

    public InventarioControlador(InventarioService inventario) {
        this.inventario = Objects.requireNonNull(inventario, "El servicio de inventario es obligatorio");
    }

    // ---------- repuestos ----------

    public Repuesto registrarRepuesto(String nombre, String marca, int repuestoTipoId,
                                      BigDecimal precioVenta, int stockInicial, int stockMinimo)
            throws SQLException {
        return inventario.registrarRepuesto(nombre, marca, repuestoTipoId, precioVenta, stockInicial, stockMinimo);
    }

    public Map<Integer, String> listarTiposDeRepuesto() throws SQLException {
        return inventario.listarTiposDeRepuesto();
    }

    public List<ReservaRepuestoDAO.Linea> listarRepuestosDeOrden(int servicioId) throws SQLException {
        return inventario.listarRepuestosDeOrden(servicioId);
    }

    public Optional<Repuesto> buscarRepuesto(int id) throws SQLException {
        return inventario.buscarRepuesto(id);
    }

    public List<Repuesto> listarRepuestos() throws SQLException {
        return inventario.listarRepuestos();
    }

    public List<Repuesto> listarRepuestosEnStockMinimo() throws SQLException {
        return inventario.listarRepuestosEnStockMinimo();
    }

    // ---------- movimientos de stock ----------

    /** Devuelve el stock resultante después de la entrada. */
    public int registrarEntrada(int repuestoId, int cantidad) throws SQLException {
        return inventario.registrarEntrada(repuestoId, cantidad);
    }

    /** Devuelve el stock resultante después de la salida; nunca queda negativo. */
    public int registrarSalida(int repuestoId, int cantidad) throws SQLException {
        return inventario.registrarSalida(repuestoId, cantidad);
    }

    public boolean hayStockSuficiente(int repuestoId, int cantidad) throws SQLException {
        return inventario.hayStockSuficiente(repuestoId, cantidad);
    }

    // ---------- proveedores ----------

    public Proveedor registrarProveedor(String nombre, String personaContacto,
                                        String telefono, String email) throws SQLException {
        return inventario.registrarProveedor(nombre, personaContacto, telefono, email);
    }

    public Optional<Proveedor> buscarProveedor(int id) throws SQLException {
        return inventario.buscarProveedor(id);
    }

    public Optional<Proveedor> buscarProveedorPorNombre(String nombre) throws SQLException {
        return inventario.buscarProveedorPorNombre(nombre);
    }

    public List<Proveedor> listarProveedores() throws SQLException {
        return inventario.listarProveedores();
    }

    /** Devuelve {@code false} si el proveedor no existe. */
    public boolean actualizarContactoProveedor(int id, String personaContacto,
                                               String telefono, String email) throws SQLException {
        return inventario.actualizarContactoProveedor(id, personaContacto, telefono, email);
    }
}
