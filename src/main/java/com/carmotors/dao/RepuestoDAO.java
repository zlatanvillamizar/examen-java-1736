package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Repuesto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Acceso a datos de la tabla {@code repuestos} con JDBC y {@link PreparedStatement}.
 *
 * <p>Los movimientos de stock son entradas y salidas directas sobre
 * {@code cantidad_stock}. {@code registrarEntrada}/{@code registrarSalida} usan su
 * propia transacción; {@code sumarStock}, {@code restarStock} y
 * {@code bloquearParaActualizar} trabajan sobre una transacción abierta por quien
 * llama (las usa el adaptador de reservas para Servicios). Los movimientos por lote
 * ({@code lotes.cantidad_disponible}) siguen pendientes de acuerdo (D4).</p>
 */
public class RepuestoDAO {

    private static final String COLUMNAS =
            "id, nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock, stock_minimo";

    private static final String SQL_LISTAR =
            "SELECT " + COLUMNAS + " FROM repuestos ORDER BY id";
    private static final String SQL_BUSCAR_POR_ID =
            "SELECT " + COLUMNAS + " FROM repuestos WHERE id = ?";
    private static final String SQL_INSERTAR =
            "INSERT INTO repuestos (nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock, stock_minimo) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_SUMAR_STOCK =
            "UPDATE repuestos SET cantidad_stock = cantidad_stock + ? WHERE id = ?";
    /** La condición {@code cantidad_stock >= ?} evita dejar el stock en negativo. */
    private static final String SQL_RESTAR_STOCK =
            "UPDATE repuestos SET cantidad_stock = cantidad_stock - ? WHERE id = ? AND cantidad_stock >= ?";
    private static final String SQL_LEER_STOCK =
            "SELECT cantidad_stock FROM repuestos WHERE id = ?";
    private static final String SQL_LISTAR_TIPOS =
            "SELECT id, nombre FROM repuesto_tipos ORDER BY id";
    private static final String SQL_BLOQUEAR_STOCK =
            "SELECT cantidad_stock FROM repuestos WHERE id = ? FOR UPDATE";

    private final FuenteConexion fuente;

    /** Usa la conexión configurada en {@link ConexionFactory}. */
    public RepuestoDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public RepuestoDAO(FuenteConexion fuente) {
        this.fuente = Objects.requireNonNull(fuente);
    }

    /** Devuelve todos los repuestos ordenados por id, o una lista vacía. */
    public List<Repuesto> listar() throws SQLException {
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_LISTAR);
             ResultSet filas = sentencia.executeQuery()) {
            List<Repuesto> repuestos = new ArrayList<>();
            while (filas.next()) {
                repuestos.add(mapear(filas));
            }
            return repuestos;
        }
    }

    /**
     * Tipos de repuesto ({@code repuesto_tipos}) ordenados por id: id → nombre.
     * Vacío si el catálogo no se ha cargado.
     */
    public Map<Integer, String> listarTipos() throws SQLException {
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_LISTAR_TIPOS);
             ResultSet filas = sentencia.executeQuery()) {
            Map<Integer, String> tipos = new LinkedHashMap<>();
            while (filas.next()) {
                tipos.put(filas.getInt("id"), filas.getString("nombre"));
            }
            return tipos;
        }
    }

    /** Devuelve un Optional vacío cuando el repuesto no existe. */
    public Optional<Repuesto> buscarPorId(int id) throws SQLException {
        validarId(id);
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BUSCAR_POR_ID)) {
            sentencia.setInt(1, id);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(mapear(filas)) : Optional.empty();
            }
        }
    }

    /**
     * Guarda un repuesto nuevo (sin id) y lo devuelve con el id asignado.
     *
     * @throws IllegalArgumentException si el repuesto ya trae id
     */
    public Repuesto registrar(Repuesto repuesto) throws SQLException {
        Objects.requireNonNull(repuesto, "El repuesto es obligatorio");
        if (repuesto.id() != null) {
            throw new IllegalArgumentException("Un repuesto nuevo no debe traer id");
        }
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia =
                     conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, repuesto.nombre());
            sentencia.setString(2, repuesto.marca());
            sentencia.setInt(3, repuesto.repuestoTipoId());
            sentencia.setBigDecimal(4, repuesto.precioVenta());
            sentencia.setInt(5, repuesto.cantidadStock());
            sentencia.setInt(6, repuesto.stockMinimo());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("MySQL no devolvió el id del repuesto insertado");
                }
                return repuesto.conId(claves.getInt(1));
            }
        }
    }

    /**
     * Suma {@code cantidad} al stock del repuesto y devuelve el stock resultante.
     *
     * @throws IllegalArgumentException      si la cantidad no es positiva
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     */
    public int registrarEntrada(int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId);
        validarCantidad(cantidad);
        return enTransaccion(conexion -> sumarStock(conexion, repuestoId, cantidad));
    }

    /**
     * Resta {@code cantidad} del stock y devuelve el stock resultante.
     * El stock nunca queda negativo; si no alcanza, no se cambia nada.
     *
     * @throws IllegalArgumentException      si la cantidad no es positiva
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     * @throws StockInsuficienteException    si no hay stock suficiente
     */
    public int registrarSalida(int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId);
        validarCantidad(cantidad);
        return enTransaccion(conexion -> restarStock(conexion, repuestoId, cantidad));
    }

    // ---------- operaciones sobre una transacción abierta por quien llama ----------
    // No abren, confirman, deshacen ni cierran la conexión: eso es responsabilidad de
    // quien coordina la transacción (por ejemplo, Servicios al reservar repuestos).

    /**
     * Suma {@code cantidad} al stock dentro de la transacción de {@code conexion}
     * y devuelve el stock resultante.
     *
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     */
    public int sumarStock(Connection conexion, int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId);
        validarCantidad(cantidad);
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_SUMAR_STOCK)) {
            sentencia.setInt(1, cantidad);
            sentencia.setInt(2, repuestoId);
            if (sentencia.executeUpdate() == 0) {
                throw new RepuestoNoEncontradoException(repuestoId);
            }
        }
        return leerStock(conexion, repuestoId)
                .orElseThrow(() -> new RepuestoNoEncontradoException(repuestoId));
    }

    /**
     * Resta {@code cantidad} del stock dentro de la transacción de {@code conexion}
     * y devuelve el stock resultante. El {@code UPDATE} condicionado impide que el
     * stock quede negativo, también con operaciones simultáneas.
     *
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     * @throws StockInsuficienteException    si no hay stock suficiente
     */
    public int restarStock(Connection conexion, int repuestoId, int cantidad) throws SQLException {
        validarId(repuestoId);
        validarCantidad(cantidad);
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_RESTAR_STOCK)) {
            sentencia.setInt(1, cantidad);
            sentencia.setInt(2, repuestoId);
            sentencia.setInt(3, cantidad);
            if (sentencia.executeUpdate() == 0) {
                int disponible = leerStock(conexion, repuestoId)
                        .orElseThrow(() -> new RepuestoNoEncontradoException(repuestoId));
                throw new StockInsuficienteException(repuestoId, cantidad, disponible);
            }
        }
        return leerStock(conexion, repuestoId)
                .orElseThrow(() -> new RepuestoNoEncontradoException(repuestoId));
    }

    /**
     * Bloquea la fila del repuesto ({@code SELECT … FOR UPDATE}) hasta que termine la
     * transacción y devuelve su stock actual. Sirve para tomar los bloqueos siempre en
     * el mismo orden (primero {@code repuestos}) y evitar interbloqueos.
     *
     * @throws RepuestoNoEncontradoException si el repuesto no existe
     */
    public int bloquearParaActualizar(Connection conexion, int repuestoId) throws SQLException {
        validarId(repuestoId);
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_BLOQUEAR_STOCK)) {
            sentencia.setInt(1, repuestoId);
            try (ResultSet filas = sentencia.executeQuery()) {
                if (!filas.next()) {
                    throw new RepuestoNoEncontradoException(repuestoId);
                }
                return filas.getInt(1);
            }
        }
    }

    private Optional<Integer> leerStock(Connection conexion, int repuestoId) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_LEER_STOCK)) {
            sentencia.setInt(1, repuestoId);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(filas.getInt(1)) : Optional.empty();
            }
        }
    }

    /** Ejecuta la operación en una transacción propia; deshace todo si algo falla. */
    private int enTransaccion(Transaccion.Trabajo<Integer> operacion) throws SQLException {
        return Transaccion.ejecutar(fuente, operacion);
    }

    private static Repuesto mapear(ResultSet fila) throws SQLException {
        return new Repuesto(
                fila.getInt("id"),
                fila.getString("nombre"),
                fila.getString("marca"),
                fila.getInt("repuesto_tipo_id"),
                fila.getBigDecimal("precio_venta"),
                fila.getInt("cantidad_stock"),
                fila.getInt("stock_minimo"));
    }

    private static void validarId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El id del repuesto debe ser positivo");
        }
    }

    private static void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad del movimiento debe ser positiva");
        }
    }
}
