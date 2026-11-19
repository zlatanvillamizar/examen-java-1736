package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Recordatorio;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecordatorioDAO {

    private final FuenteConexion fuente;

    public RecordatorioDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public RecordatorioDAO(FuenteConexion fuente) {
        this.fuente = java.util.Objects.requireNonNull(fuente);
    }

    private static final String INSERT =
            "INSERT INTO recordatorios_mantenimiento "
                    + "(vehiculo_id, servicio_origen_id, fecha_proximo_mantenimiento, descripcion, notificado) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT * FROM recordatorios_mantenimiento WHERE id = ?";
    private static final String SELECT_PENDIENTES =
            "SELECT * FROM recordatorios_mantenimiento WHERE notificado = FALSE "
                    + "ORDER BY fecha_proximo_mantenimiento";
    private static final String SELECT_BY_VEHICULO =
            "SELECT * FROM recordatorios_mantenimiento WHERE vehiculo_id = ? "
                    + "ORDER BY fecha_proximo_mantenimiento";
    private static final String UPDATE_NOTIFICADO =
            "UPDATE recordatorios_mantenimiento SET notificado = TRUE WHERE id = ? AND notificado = FALSE";

    // ---------- consultas de alertas (derivadas, sin tablas nuevas) ----------
    private static final String SELECT_VENCIDOS =
            "SELECT * FROM recordatorios_mantenimiento "
                    + "WHERE notificado = FALSE AND fecha_proximo_mantenimiento < ? "
                    + "ORDER BY fecha_proximo_mantenimiento";
    private static final String SELECT_PROXIMOS =
            "SELECT * FROM recordatorios_mantenimiento "
                    + "WHERE notificado = FALSE AND fecha_proximo_mantenimiento BETWEEN ? AND ? "
                    + "ORDER BY fecha_proximo_mantenimiento";
    private static final String SELECT_ALERTAS_VEHICULO =
            "SELECT * FROM recordatorios_mantenimiento "
                    + "WHERE vehiculo_id = ? AND notificado = FALSE AND fecha_proximo_mantenimiento <= ? "
                    + "ORDER BY fecha_proximo_mantenimiento";

    public Recordatorio crear(Recordatorio r) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getVehiculoId());
            if (r.getServicioOrigenId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, r.getServicioOrigenId());
            }
            ps.setDate(3, Date.valueOf(r.getFechaProximoMantenimiento()));
            ps.setString(4, r.getDescripcion());
            ps.setBoolean(5, r.isNotificado());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setId(keys.getInt(1));
            }
            return r;
        }
    }

    public Optional<Recordatorio> buscarPorId(int id) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public List<Recordatorio> listarPendientes() throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_PENDIENTES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    public List<Recordatorio> listarPorVehiculo(int vehiculoId) throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_VEHICULO)) {
            ps.setInt(1, vehiculoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public boolean marcarNotificado(int id) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(UPDATE_NOTIFICADO)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ---------- alertas ----------

    /** Sin notificar y con fecha anterior a 'hoy'. */
    public List<Recordatorio> listarVencidos(LocalDate hoy) throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_VENCIDOS)) {
            ps.setDate(1, Date.valueOf(hoy));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Sin notificar y con fecha entre 'desde' y 'hasta' (ambas incluidas). */
    public List<Recordatorio> listarProximos(LocalDate desde, LocalDate hasta) throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_PROXIMOS)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Sin notificar de un vehículo, con fecha hasta 'limite' (incluye vencidas). */
    public List<Recordatorio> listarAlertasPorVehiculo(int vehiculoId, LocalDate limite)
            throws SQLException {
        List<Recordatorio> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_ALERTAS_VEHICULO)) {
            ps.setInt(1, vehiculoId);
            ps.setDate(2, Date.valueOf(limite));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private Recordatorio mapear(ResultSet rs) throws SQLException {
        Recordatorio r = new Recordatorio();
        r.setId(rs.getInt("id"));
        r.setVehiculoId(rs.getInt("vehiculo_id"));
        int servicio = rs.getInt("servicio_origen_id");
        r.setServicioOrigenId(rs.wasNull() ? null : servicio);
        r.setFechaProximoMantenimiento(rs.getDate("fecha_proximo_mantenimiento").toLocalDate());
        r.setDescripcion(rs.getString("descripcion"));
        r.setNotificado(rs.getBoolean("notificado"));
        return r;
    }
}
