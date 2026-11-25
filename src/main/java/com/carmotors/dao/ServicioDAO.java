package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.TiempoEntrega;
import com.carmotors.servicio.taller.RepositorioConsultaOrdenServicio;
import com.carmotors.servicio.taller.dominio.IdentificadorServicio;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ServicioDAO implements RepositorioConsultaOrdenServicio<IdentificadorServicio, Servicio> {
    private static final String CONSULTA = """
            SELECT id, vehiculo_id, mantenimiento_tipo_id, fecha_recepcion, fecha_entrega,
                   descripcion_problema, costo_mano_obra, estado_id
            FROM servicios
            """;

    public IdentificadorServicio registrar(Servicio servicio) throws SQLException {
        try (var conexion = ConexionFactory.obtenerConexion()) {
            return registrar(conexion, servicio);
        }
    }

    public IdentificadorServicio registrar(Connection conexion, Servicio servicio) throws SQLException {
        String sql = """
                INSERT INTO servicios (vehiculo_id, mantenimiento_tipo_id, fecha_recepcion,
                    fecha_entrega, descripcion_problema, costo_mano_obra, estado_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setInt(1, servicio.vehiculoId());
            sentencia.setInt(2, servicio.mantenimientoTipoId());
            sentencia.setObject(3, servicio.fechaRecepcion());
            sentencia.setObject(4, servicio.fechaEntrega());
            sentencia.setString(5, servicio.descripcionProblema());
            sentencia.setBigDecimal(6, servicio.costoManoObra());
            sentencia.setInt(7, servicio.estadoId());
            sentencia.executeUpdate();
            try (var claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("MySQL no devolvió el ID del servicio");
                }
                return new IdentificadorServicio(claves.getInt(1));
            }
        }
    }

    @Override
    public Optional<Servicio> buscarPorId(IdentificadorServicio id) {
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(CONSULTA + " WHERE id = ?")) {
            sentencia.setLong(1, id.valor());
            try (var filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leer(filas)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo consultar el servicio", error);
        }
    }

    @Override
    public List<Servicio> listar() {
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(CONSULTA + " ORDER BY id");
             var filas = sentencia.executeQuery()) {
            List<Servicio> servicios = new ArrayList<>();
            while (filas.next()) {
                servicios.add(leer(filas));
            }
            return servicios;
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudieron listar los servicios", error);
        }
    }

    public record ServicioConFechas(Servicio servicio, String tipoMantenimiento) {
    }

    public List<ServicioConFechas> listarCompletadosConFechas() throws SQLException {
        String sql = """
                SELECT s.*, m.nombre AS tipo_mantenimiento
                FROM servicios s
                JOIN mantenimiento_tipos m ON m.id = s.mantenimiento_tipo_id
                JOIN servicio_estados e ON e.id = s.estado_id
                WHERE s.fecha_recepcion IS NOT NULL AND s.fecha_entrega IS NOT NULL
                  AND LOWER(e.nombre) IN ('completado', 'finalizado')
                """;
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql);
             var filas = sentencia.executeQuery()) {
            List<ServicioConFechas> completados = new ArrayList<>();
            while (filas.next()) {
                completados.add(new ServicioConFechas(leer(filas), filas.getString("tipo_mantenimiento")));
            }
            return completados;
        }
    }

    public Optional<TiempoEntrega> buscarTiempoEntrega(int servicioId) throws SQLException {
        String sql = """
                SELECT duracion_estimada_segundos, fecha_entrega_estimada
                FROM tiempo_entrega WHERE servicio_id = ?
                """;
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, servicioId);
            try (var filas = sentencia.executeQuery()) {
                if (!filas.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TiempoEntrega(servicioId,
                        Duration.ofSeconds(filas.getLong("duracion_estimada_segundos")),
                        filas.getObject("fecha_entrega_estimada", LocalDateTime.class)));
            }
        }
    }

    public void guardarTiempoEntrega(TiempoEntrega estimacion) throws SQLException {
        String sql = """
                INSERT INTO tiempo_entrega (servicio_id, duracion_estimada_segundos, fecha_entrega_estimada)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE servicio_id = tiempo_entrega.servicio_id
                """;
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, estimacion.servicioId());
            sentencia.setLong(2, estimacion.duracionEstimada().toSeconds());
            sentencia.setObject(3, estimacion.fechaEntregaEstimada());
            sentencia.executeUpdate();
        }
    }

    public boolean actualizarEstado(IdentificadorServicio id, int estadoId) throws SQLException {
        try (var conexion = ConexionFactory.obtenerConexion()) {
            return actualizarEstado(conexion, id, estadoId);
        }
    }

    public boolean actualizarEstado(Connection conexion, IdentificadorServicio id, int estadoId)
            throws SQLException {
        String sql = "UPDATE servicios SET estado_id = ? WHERE id = ?";
        try (var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, estadoId);
            sentencia.setLong(2, id.valor());
            return sentencia.executeUpdate() != 0;
        }
    }

    public Optional<Integer> bloquearEstadoActual(Connection conexion, IdentificadorServicio id)
            throws SQLException {
        try (var sentencia = conexion.prepareStatement("SELECT estado_id FROM servicios WHERE id = ? FOR UPDATE")) {
            sentencia.setLong(1, id.valor());
            try (var filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(filas.getInt(1)) : Optional.empty();
            }
        }
    }

    public boolean tieneFactura(Connection conexion, IdentificadorServicio id) throws SQLException {
        try (var sentencia = conexion.prepareStatement("SELECT 1 FROM facturas WHERE servicio_id = ?")) {
            sentencia.setLong(1, id.valor());
            try (var filas = sentencia.executeQuery()) {
                return filas.next();
            }
        }
    }

    public Optional<String> buscarNombreEstado(Connection conexion, int estadoId) throws SQLException {
        try (var sentencia = conexion.prepareStatement("SELECT nombre FROM servicio_estados WHERE id = ?")) {
            sentencia.setInt(1, estadoId);
            try (var filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(filas.getString(1)) : Optional.empty();
            }
        }
    }

    public boolean actualizarFechaEntrega(IdentificadorServicio id, LocalDateTime fechaEntrega)
            throws SQLException {
        String sql = "UPDATE servicios SET fecha_entrega = ? WHERE id = ?";
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, fechaEntrega);
            sentencia.setLong(2, id.valor());
            return sentencia.executeUpdate() != 0;
        }
    }

    public boolean asignarTecnico(ServicioTecnico asignacion) throws SQLException {
        // La inserción vuelve a comprobar el estado por si cambió desde la consulta del servicio.
        String sql = """
                INSERT INTO servicios_tecnicos (servicio_id, tecnico_id, fecha_asignacion)
                SELECT ?, id, ? FROM tecnicos WHERE id = ? AND activo = TRUE
                """;
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, asignacion.servicioId());
            sentencia.setObject(2, asignacion.fechaAsignacion());
            sentencia.setInt(3, asignacion.tecnicoId());
            return sentencia.executeUpdate() != 0;
        }
    }

    private Servicio leer(ResultSet fila) throws SQLException {
        return new Servicio(fila.getInt("id"), fila.getInt("vehiculo_id"),
                fila.getInt("mantenimiento_tipo_id"),
                fila.getObject("fecha_recepcion", LocalDateTime.class),
                fila.getObject("fecha_entrega", LocalDateTime.class),
                fila.getString("descripcion_problema"), fila.getBigDecimal("costo_mano_obra"),
                fila.getInt("estado_id"));
    }
}
