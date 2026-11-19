package com.carmotors.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a las cantidades reservadas y usadas de {@code servicios_repuestos}
 * (definida en {@code database/bloques/servicios.sql}).
 *
 * <p>Todas las operaciones trabajan sobre la transacción de la {@code conexion}
 * recibida: no la abren, confirman, deshacen ni cierran. {@code cantidad_reservada}
 * guarda el <b>saldo pendiente</b> (reservado y aún no consumido ni liberado).</p>
 */
public class ReservaRepuestoDAO {

    /** Fila de {@code servicios_repuestos}. */
    public record Linea(int servicioId, int repuestoId, int cantidadReservada,
                        int cantidadUsada, BigDecimal precioUnitarioVenta) {
    }

    private static final String COLUMNAS =
            "servicio_id, repuesto_id, cantidad_reservada, cantidad_usada, precio_unitario_venta";

    /**
     * Crea la línea con el precio vigente del repuesto o, si ya existe, suma al saldo
     * reservado conservando el precio con el que se creó.
     */
    private static final String SQL_SUMAR_RESERVA =
            "INSERT INTO servicios_repuestos (" + COLUMNAS + ") "
                    + "SELECT ?, id, ?, 0, precio_venta FROM repuestos WHERE id = ? "
                    + "ON DUPLICATE KEY UPDATE cantidad_reservada = cantidad_reservada + ?";
    /** Pasa unidades del saldo reservado a usadas, solo si el saldo alcanza. */
    private static final String SQL_CONSUMIR =
            "UPDATE servicios_repuestos SET cantidad_reservada = cantidad_reservada - ?, "
                    + "cantidad_usada = cantidad_usada + ? "
                    + "WHERE servicio_id = ? AND repuesto_id = ? AND cantidad_reservada >= ?";
    /** Quita unidades del saldo reservado sin tocar las usadas, solo si el saldo alcanza. */
    private static final String SQL_LIBERAR =
            "UPDATE servicios_repuestos SET cantidad_reservada = cantidad_reservada - ? "
                    + "WHERE servicio_id = ? AND repuesto_id = ? AND cantidad_reservada >= ?";
    private static final String SQL_BUSCAR =
            "SELECT " + COLUMNAS + " FROM servicios_repuestos WHERE servicio_id = ? AND repuesto_id = ?";
    private static final String SQL_BUSCAR_PARA_ACTUALIZAR = SQL_BUSCAR + " FOR UPDATE";
    private static final String SQL_LISTAR_POR_SERVICIO =
            "SELECT " + COLUMNAS + " FROM servicios_repuestos WHERE servicio_id = ? ORDER BY repuesto_id";

    /**
     * Suma {@code cantidad} al saldo reservado de la línea (servicio, repuesto),
     * creándola con {@code repuestos.precio_venta} si no existe.
     *
     * @return {@code false} si el repuesto no existe (no se insertó nada)
     */
    public boolean sumarReserva(Connection conexion, int servicioId, int repuestoId, int cantidad)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_SUMAR_RESERVA)) {
            sentencia.setInt(1, servicioId);
            sentencia.setInt(2, cantidad);
            sentencia.setInt(3, repuestoId);
            sentencia.setInt(4, cantidad);
            return sentencia.executeUpdate() > 0;
        }
    }

    /**
     * Consume {@code cantidad} del saldo reservado: lo resta de {@code cantidad_reservada}
     * y lo suma a {@code cantidad_usada}.
     *
     * @return {@code false} si la línea no existe o su saldo no alcanza (no cambia nada)
     */
    public boolean consumir(Connection conexion, int servicioId, int repuestoId, int cantidad)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_CONSUMIR)) {
            sentencia.setInt(1, cantidad);
            sentencia.setInt(2, cantidad);
            sentencia.setInt(3, servicioId);
            sentencia.setInt(4, repuestoId);
            sentencia.setInt(5, cantidad);
            return sentencia.executeUpdate() > 0;
        }
    }

    /**
     * Libera {@code cantidad} del saldo reservado; {@code cantidad_usada} no cambia.
     *
     * @return {@code false} si la línea no existe o su saldo no alcanza (no cambia nada)
     */
    public boolean liberar(Connection conexion, int servicioId, int repuestoId, int cantidad)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_LIBERAR)) {
            sentencia.setInt(1, cantidad);
            sentencia.setInt(2, servicioId);
            sentencia.setInt(3, repuestoId);
            sentencia.setInt(4, cantidad);
            return sentencia.executeUpdate() > 0;
        }
    }

    /** Devuelve la línea (servicio, repuesto), o vacío si no existe. */
    public Optional<Linea> buscar(Connection conexion, int servicioId, int repuestoId) throws SQLException {
        return buscar(conexion, SQL_BUSCAR, servicioId, repuestoId);
    }

    /** Igual que {@link #buscar}, pero bloquea la fila hasta que termine la transacción. */
    public Optional<Linea> buscarParaActualizar(Connection conexion, int servicioId, int repuestoId)
            throws SQLException {
        return buscar(conexion, SQL_BUSCAR_PARA_ACTUALIZAR, servicioId, repuestoId);
    }

    /** Líneas de un servicio ordenadas por repuesto (lista vacía si no tiene). */
    public List<Linea> listarPorServicio(Connection conexion, int servicioId) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_LISTAR_POR_SERVICIO)) {
            sentencia.setInt(1, servicioId);
            try (ResultSet filas = sentencia.executeQuery()) {
                List<Linea> lineas = new ArrayList<>();
                while (filas.next()) {
                    lineas.add(mapear(filas));
                }
                return lineas;
            }
        }
    }

    private Optional<Linea> buscar(Connection conexion, String sql, int servicioId, int repuestoId)
            throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, servicioId);
            sentencia.setInt(2, repuestoId);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(mapear(filas)) : Optional.empty();
            }
        }
    }

    private static Linea mapear(ResultSet fila) throws SQLException {
        return new Linea(
                fila.getInt("servicio_id"),
                fila.getInt("repuesto_id"),
                fila.getInt("cantidad_reservada"),
                fila.getInt("cantidad_usada"),
                fila.getBigDecimal("precio_unitario_venta"));
    }
}
