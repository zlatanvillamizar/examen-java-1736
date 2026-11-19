package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.ServicioFacturable;
import com.carmotors.servicio.taller.dominio.IdentificadorServicio;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FacturaDAO {

    private final FuenteConexion fuente;

    public FacturaDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public FacturaDAO(FuenteConexion fuente) {
        this.fuente = Objects.requireNonNull(fuente);
    }

    private static final String INSERT =
            "INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos, "
                    + "total_mano_obra, impuestos, total_final, cliente_nombre, cliente_documento, "
                    + "vehiculo_descripcion, detalle_historico) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)";
    private static final String INSERT_DETALLE =
            "INSERT INTO factura_detalles (factura_id, repuesto_id, nombre_repuesto, cantidad, precio_unitario) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_DETALLE_HISTORICO =
            "SELECT repuesto_id, nombre_repuesto, cantidad, precio_unitario "
                    + "FROM factura_detalles WHERE factura_id = ? ORDER BY repuesto_id";
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
        try (Connection con = fuente.obtener()) {
            return insertarEnTransaccion(con, f, true);
        }
    }

    Factura insertarEnTransaccion(Connection con, Factura f) throws SQLException {
        return insertarEnTransaccion(con, f, false);
    }

    private Factura insertarEnTransaccion(Connection con, Factura f, boolean validarOrigen) throws SQLException {
        boolean autoCommitInicial = con.getAutoCommit();
        con.setAutoCommit(false);
        int id;
        try {
            if (validarOrigen) {
                verificarOrigenBajoBloqueo(con, f);
            }
            try (PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, f.getServicioId());
                ps.setTimestamp(2, Timestamp.valueOf(f.getFechaEmision()));
                ps.setBigDecimal(3, f.getTotalRepuestos());
                ps.setBigDecimal(4, f.getTotalManoObra());
                ps.setBigDecimal(5, f.getImpuestos());
                ps.setBigDecimal(6, f.getTotalFinal());
                ps.setString(7, f.getClienteNombre());
                ps.setString(8, f.getClienteDocumento());
                ps.setString(9, f.getVehiculoDescripcion());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("MySQL no devolvió el ID de la factura");
                    }
                    id = keys.getInt(1);
                }
            }
            insertarDetalles(con, id, f.getDetalles());
            con.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                con.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
                // Volver a autoCommit podría confirmar una transacción que sigue abierta.
                throw e;
            }
            restaurarAutoCommit(con, autoCommitInicial, e);
            throw e;
        }
        f.setId(id);
        restaurarAutoCommit(con, autoCommitInicial, null);
        return f;
    }

    private void verificarOrigenBajoBloqueo(Connection con, Factura f) throws SQLException {
        // Servicios toma este mismo bloqueo antes de modificar el consumo o el estado.
        var id = new IdentificadorServicio(f.getServicioId());
        if (new ServicioDAO().bloquearEstadoActual(con, id).isEmpty()) {
            throw new IllegalArgumentException("No existe el servicio " + id.valor());
        }
        var actual = buscarServicioFacturable(con, f.getServicioId()).orElseThrow();
        BigDecimal manoObra = actual.costoManoObra() == null ? BigDecimal.ZERO : actual.costoManoObra();
        if (!"Completado".equalsIgnoreCase(actual.estado())
                || manoObra.compareTo(f.getTotalManoObra()) != 0
                || !actual.detalles().equals(f.getDetalles())
                || !Objects.equals(actual.clienteNombre(), f.getClienteNombre())
                || !Objects.equals(actual.clienteDocumento(), f.getClienteDocumento())
                || !Objects.equals(actual.vehiculoDescripcion(), f.getVehiculoDescripcion())) {
            throw new IllegalStateException("El servicio cambió durante la emisión. Consulte su estado y reintente.");
        }
    }

    private void insertarDetalles(Connection con, int facturaId, List<DetalleFactura> detalles) throws SQLException {
        if (detalles.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(INSERT_DETALLE)) {
            for (DetalleFactura detalle : detalles) {
                ps.setInt(1, facturaId);
                ps.setInt(2, detalle.repuestoId());
                ps.setString(3, detalle.nombreRepuesto());
                ps.setInt(4, detalle.cantidad());
                ps.setBigDecimal(5, detalle.precioUnitario());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void restaurarAutoCommit(Connection con, boolean autoCommitInicial, Exception causaPrincipal)
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
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Factura factura = mapear(rs);
                if (factura.isDetalleHistorico()) {
                    factura.setDetalles(cargarDetalleHistorico(con, id));
                }
                return Optional.of(factura);
            }
        }
    }

    public Optional<Factura> buscarPorServicio(int servicioId) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_SERVICIO)) {
            ps.setInt(1, servicioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public List<Factura> listar() throws SQLException {
        List<Factura> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /** Lee servicio + vehículo + cliente + repuestos usados. Vacío si falta alguna relación. */
    public Optional<ServicioFacturable> buscarServicioFacturable(int servicioId) throws SQLException {
        try (Connection con = fuente.obtener()) {
            return buscarServicioFacturable(con, servicioId);
        }
    }

    private Optional<ServicioFacturable> buscarServicioFacturable(Connection con, int servicioId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_SERVICIO)) {
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

    private List<DetalleFactura> cargarDetalleHistorico(Connection con, int facturaId) throws SQLException {
        var detalles = new ArrayList<DetalleFactura>();
        try (PreparedStatement ps = con.prepareStatement(SELECT_DETALLE_HISTORICO)) {
            ps.setInt(1, facturaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    detalles.add(new DetalleFactura(rs.getInt("repuesto_id"), rs.getString("nombre_repuesto"),
                            rs.getInt("cantidad"), rs.getBigDecimal("precio_unitario")));
                }
            }
        }
        return detalles;
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
        f.setClienteNombre(rs.getString("cliente_nombre"));
        f.setClienteDocumento(rs.getString("cliente_documento"));
        f.setVehiculoDescripcion(rs.getString("vehiculo_descripcion"));
        f.setDetalleHistorico(rs.getBoolean("detalle_historico"));
        return f;
    }
}
