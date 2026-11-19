package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.ServicioFacturable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FacturaDAO {

    private static final String INSERT =
            "INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos, "
                    + "total_mano_obra, impuestos, total_final) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT * FROM facturas WHERE id = ?";
    private static final String SELECT_BY_SERVICIO =
            "SELECT * FROM facturas WHERE servicio_id = ?";
    private static final String SELECT_ALL =
            "SELECT * FROM facturas ORDER BY fecha_emision DESC";

    private static final String SELECT_SERVICIO =
            "SELECT s.id, s.costo_mano_obra, e.nombre AS estado, "
                    + "v.placa, v.marca, v.modelo, "
                    + "c.nombre AS cliente_nombre, c.documento_identidad "
                    + "FROM servicios s "
                    + "JOIN servicio_estados e ON e.id = s.estado_id "
                    + "JOIN vehiculos v ON v.id = s.vehiculo_id "
                    + "JOIN clientes c ON c.id = v.cliente_id "
                    + "WHERE s.id = ?";
    private static final String SELECT_DETALLES =
            "SELECT sr.repuesto_id, r.nombre, sr.cantidad_usada, sr.precio_unitario_venta "
                    + "FROM servicios_repuestos sr "
                    + "JOIN repuestos r ON r.id = sr.repuesto_id "
                    + "WHERE sr.servicio_id = ? AND sr.cantidad_usada > 0 "
                    + "ORDER BY sr.repuesto_id";

    /** Inserta la factura dentro de una transacción (commit, o rollback si falla). */
    public Factura crear(Factura f) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion()) {
            boolean autoCommitInicial = con.getAutoCommit();
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, f.getServicioId());
                ps.setTimestamp(2, Timestamp.valueOf(f.getFechaEmision()));
                ps.setBigDecimal(3, f.getTotalRepuestos());
                ps.setBigDecimal(4, f.getTotalManoObra());
                ps.setBigDecimal(5, f.getImpuestos());
                ps.setBigDecimal(6, f.getTotalFinal());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) f.setId(keys.getInt(1));
                }
                con.commit();
                restaurarAutoCommit(con, autoCommitInicial, null);
                return f;
            } catch (SQLException e) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    // Error secundario al revertir: se conserva en la excepción principal.
                    e.addSuppressed(rollbackEx);
                }
                restaurarAutoCommit(con, autoCommitInicial, e);
                throw e;
            }
        }
    }

    private void restaurarAutoCommit(Connection con, boolean autoCommitInicial, SQLException causaPrincipal)
            throws SQLException {
        try {
            con.setAutoCommit(autoCommitInicial);
        } catch (SQLException restoreEx) {
            if (causaPrincipal != null) {
                // Error secundario al restaurar autoCommit tras un fallo previo.
                causaPrincipal.addSuppressed(restoreEx);
            } else {
                throw restoreEx;
            }
        }
    }

    public Optional<Factura> buscarPorId(int id) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Factura> buscarPorServicio(int servicioId) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_SERVICIO)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public List<Factura> listar() throws SQLException {
        List<Factura> lista = new ArrayList<>();
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /** Lee servicio + vehículo + cliente + repuestos usados. Vacío si falta alguna relación. */
    public Optional<ServicioFacturable> buscarServicioFacturable(int servicioId) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SELECT_SERVICIO)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BigDecimal manoObra = rs.getBigDecimal("costo_mano_obra");
                String vehiculo = rs.getString("placa") + " - "
                        + rs.getString("marca") + " " + rs.getString("modelo");
                ServicioFacturable base = new ServicioFacturable(
                        rs.getInt("id"), rs.getString("estado"), manoObra,
                        rs.getString("cliente_nombre"), rs.getString("documento_identidad"),
                        vehiculo, cargarDetalles(con, servicioId));
                return Optional.of(base);
            }
        }
    }

    private List<DetalleFactura> cargarDetalles(Connection con, int servicioId) throws SQLException {
        List<DetalleFactura> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(SELECT_DETALLES)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cantidadUsada = rs.getInt("cantidad_usada");
                    if (cantidadUsada <= 0) {
                        continue;
                    }
                    lista.add(new DetalleFactura(
                            rs.getInt("repuesto_id"),
                            rs.getString("nombre"),
                            cantidadUsada,
                            rs.getBigDecimal("precio_unitario_venta")));
                }
            }
        }
        return lista;
    }

    private Factura mapear(ResultSet rs) throws SQLException {
        Factura f = new Factura();
        f.setId(rs.getInt("id"));
        f.setServicioId(rs.getInt("servicio_id"));
        f.setFechaEmision(rs.getTimestamp("fecha_emision").toLocalDateTime());
        f.setTotalRepuestos(rs.getBigDecimal("total_repuestos"));
        f.setTotalManoObra(rs.getBigDecimal("total_mano_obra"));
        f.setImpuestos(rs.getBigDecimal("impuestos"));
        f.setTotalFinal(rs.getBigDecimal("total_final"));
        return f;
    }
}