package com.carmotors.servicios.inventario;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.modelo.Repuesto;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reglas de negocio del módulo de Inventario: repuestos, movimientos de stock y proveedores.
 *
 * <p>Reglas de stock que aplica:</p>
 * <ul>
 *   <li>Toda entrada o salida tiene una cantidad positiva y un repuesto existente.</li>
 *   <li>El stock nunca queda negativo: una salida mayor al stock se rechaza sin cambiar nada.</li>
 *   <li>Una entrada no puede superar el máximo de la columna {@code INT}.</li>
 * </ul>
 *
 * <p>Las reservas, consumos y liberaciones de repuestos para Servicios están en
 * {@link InventarioServiciosJdbc}, que trabaja dentro de la transacción que coordina
 * Servicios (issue #53, {@code docs/inventario/integracion-servicios.md}).</p>
 */
public class InventarioService {

    private final RepuestoDAO repuestos;
    private final ProveedorDAO proveedores;
    private final ConsultaReservas reservas;

    public InventarioService() {
        this(new RepuestoDAO(), new ProveedorDAO(), ConsultaReservas.jdbc(ConexionFactory::obtenerConexion));
    }

    public InventarioService(RepuestoDAO repuestos, ProveedorDAO proveedores) {
        this(repuestos, proveedores, ConsultaReservas.jdbc(ConexionFactory::obtenerConexion));
    }

    public InventarioService(RepuestoDAO repuestos, ProveedorDAO proveedores, ConsultaReservas reservas) {
        this.repuestos = Objects.requireNonNull(repuestos, "El DAO de repuestos es obligatorio");
        this.proveedores = Objects.requireNonNull(proveedores, "El DAO de proveedores es obligatorio");
        this.reservas = Objects.requireNonNull(reservas, "La consulta de reservas es obligatoria");
    }

    // ---------- repuestos ----------

    /** Registra un repuesto nuevo; las validaciones de datos están en {@link Repuesto}. */
    public Repuesto registrarRepuesto(String nombre, String marca, int repuestoTipoId,
                                      BigDecimal precioVenta, int stockInicial, int stockMinimo)
            throws SQLException {
        return repuestos.registrar(
                Repuesto.nuevo(nombre, marca, repuestoTipoId, precioVenta, stockInicial, stockMinimo));
    }

    /** Tipos de repuesto disponibles (id → nombre), ordenados por id. */
    public Map<Integer, String> listarTiposDeRepuesto() throws SQLException {
        return repuestos.listarTipos();
    }

    /**
     * Repuestos reservados y usados por una orden de servicio, ordenados por repuesto.
     *
     * @throws IllegalArgumentException si el id de la orden no es positivo
     */
    public List<ReservaRepuestoDAO.Linea> listarRepuestosDeOrden(int servicioId) throws SQLException {
        validarId(servicioId, "servicio");
        return List.copyOf(reservas.listarPorServicio(servicioId));
    }

    public Optional<Repuesto> buscarRepuesto(int id) throws SQLException {
        validarId(id, "repuesto");
        return repuestos.buscarPorId(id);
    }

    public List<Repuesto> listarRepuestos() throws SQLException {
        return List.copyOf(repuestos.listar());
    }

    /** Repuestos cuyo stock está en el mínimo o por debajo (para alertas de reposición). */
    public List<Repuesto> listarRepuestosEnStockMinimo() throws SQLException {
        return repuestos.listar().stream()
                .filter(Repuesto::estaEnStockMinimo)
                .toList();
    }

    /**
     * Registra la entrada de {@code cantidad} unidades y devuelve el stock resultante.
     *
     * @throws IllegalArgumentException      si la cantidad no es positiva o desbordaría el stock
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     */
    public int registrarEntrada(int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId, "repuesto");
        validarCantidad(cantidad);
        Repuesto actual = repuestoExistente(repuestoId);
        if (cantidad > Integer.MAX_VALUE - actual.cantidadStock()) {
            throw new IllegalArgumentException("La entrada supera el stock máximo permitido");
        }
        return repuestos.registrarEntrada(repuestoId, cantidad);
    }

    /**
     * Registra la salida de {@code cantidad} unidades y devuelve el stock resultante.
     * Si no hay stock suficiente no cambia nada.
     *
     * @throws IllegalArgumentException      si la cantidad no es positiva
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     * @throws StockInsuficienteException    si la salida dejaría el stock en negativo
     */
    public int registrarSalida(int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId, "repuesto");
        validarCantidad(cantidad);
        Repuesto actual = repuestoExistente(repuestoId);
        if (cantidad > actual.cantidadStock()) {
            throw new StockInsuficienteException(repuestoId, cantidad, actual.cantidadStock());
        }
        // El DAO vuelve a comprobar el stock dentro del UPDATE, por si otra
        // operación lo cambió entre la consulta y la salida.
        return repuestos.registrarSalida(repuestoId, cantidad);
    }

    /** {@code true} si hay al menos {@code cantidad} unidades en stock. */
    public boolean hayStockSuficiente(int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId, "repuesto");
        validarCantidad(cantidad);
        return repuestoExistente(repuestoId).cantidadStock() >= cantidad;
    }

    // ---------- proveedores ----------

    /**
     * Registra un proveedor nuevo; las validaciones de datos están en {@link Proveedor}.
     *
     * @throws IllegalStateException si ya existe un proveedor con ese nombre
     */
    public Proveedor registrarProveedor(String nombre, String personaContacto,
                                        String telefono, String email) throws SQLException {
        Proveedor nuevo = Proveedor.nuevo(nombre, personaContacto, telefono, email);
        if (proveedores.buscarPorNombre(nuevo.nombre()).isPresent()) {
            throw new IllegalStateException("Ya existe un proveedor con el nombre " + nuevo.nombre());
        }
        return proveedores.registrar(nuevo);
    }

    public Optional<Proveedor> buscarProveedor(int id) throws SQLException {
        validarId(id, "proveedor");
        return proveedores.buscarPorId(id);
    }

    /**
     * Busca un proveedor por su nombre exacto (único en {@code proveedores}),
     * ignorando espacios al inicio y al final.
     *
     * @throws IllegalArgumentException si el nombre está vacío
     */
    public Optional<Proveedor> buscarProveedorPorNombre(String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
        return proveedores.buscarPorNombre(nombre);
    }

    public List<Proveedor> listarProveedores() throws SQLException {
        return List.copyOf(proveedores.listar());
    }

    /**
     * Actualiza persona de contacto, teléfono y email de un proveedor existente.
     *
     * @return {@code false} si el proveedor no existe
     */
    public boolean actualizarContactoProveedor(int id, String personaContacto,
                                               String telefono, String email) throws SQLException {
        validarId(id, "proveedor");
        Optional<Proveedor> actual = proveedores.buscarPorId(id);
        if (actual.isEmpty()) {
            return false;
        }
        Proveedor actualizado = new Proveedor(id, actual.get().nombre(), personaContacto, telefono, email);
        return proveedores.actualizarContacto(actualizado);
    }

    // ---------- validaciones privadas ----------

    private Repuesto repuestoExistente(int repuestoId) throws SQLException {
        return repuestos.buscarPorId(repuestoId)
                .orElseThrow(() -> new RepuestoNoEncontradoException(repuestoId));
    }

    private static void validarId(int id, String entidad) {
        if (id <= 0) {
            throw new IllegalArgumentException("El id del " + entidad + " debe ser positivo");
        }
    }

    private static void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad del movimiento debe ser positiva");
        }
    }
}
