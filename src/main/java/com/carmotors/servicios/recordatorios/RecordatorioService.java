package com.carmotors.servicios.recordatorios;

import com.carmotors.dao.RecordatorioDAO;
import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Recordatorio;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RecordatorioService {

    private static final int MAX_DESCRIPCION = 255;

    private final RecordatorioDAO recordatorioDAO;

    public RecordatorioService() {
        this(new RecordatorioDAO());
    }

    public RecordatorioService(RecordatorioDAO recordatorioDAO) {
        if (recordatorioDAO == null) {
            throw new IllegalArgumentException("El DAO es obligatorio");
        }
        this.recordatorioDAO = recordatorioDAO;
    }

    public Recordatorio crear(int vehiculoId, Integer servicioOrigenId,
                              LocalDate fecha, String descripcion) throws SQLException {
        validarId(vehiculoId, "El vehículo");
        if (servicioOrigenId != null) {
            validarId(servicioOrigenId, "El servicio de origen");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        if (fecha.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede estar en el pasado");
        }
        String desc = descripcionOpcional(descripcion);
        return recordatorioDAO.crear(new Recordatorio(vehiculoId, servicioOrigenId, fecha, desc));
    }

    public Optional<Recordatorio> buscarPorId(int id) throws SQLException {
        validarId(id, "El ID");
        return recordatorioDAO.buscarPorId(id);
    }

    public List<Recordatorio> listarPendientes() throws SQLException {
        return recordatorioDAO.listarPendientes();
    }

    /** Pendientes cuya fecha llega dentro de los próximos 'dias' días (incluye vencidos). */
    public List<Recordatorio> listarProximos(int dias) throws SQLException {
        validarDias(dias);
        LocalDate limite = LocalDate.now().plusDays(dias);
        return recordatorioDAO.listarPendientes().stream()
                .filter(r -> !r.getFechaProximoMantenimiento().isAfter(limite))
                .sorted(Comparator.comparing(Recordatorio::getFechaProximoMantenimiento))
                .toList();
    }

    public List<Recordatorio> listarPorVehiculo(int vehiculoId) throws SQLException {
        validarId(vehiculoId, "El vehículo");
        return recordatorioDAO.listarPorVehiculo(vehiculoId);
    }

    public boolean marcarNotificado(int id) throws SQLException {
        validarId(id, "El ID");
        return recordatorioDAO.marcarNotificado(id);
    }

    // ---------- alertas (derivadas de los recordatorios) ----------

    /** Alertas vencidas: sin notificar y con fecha anterior a hoy. */
    public List<Alerta> listarAlertasVencidas() throws SQLException {
        LocalDate hoy = LocalDate.now();
        return recordatorioDAO.listarVencidos(hoy).stream()
                .map(r -> Alerta.desde(r, hoy))
                .toList();
    }

    /** Alertas próximas: sin notificar, desde hoy hasta hoy + dias (sin vencidas). */
    public List<Alerta> listarAlertasProximas(int dias) throws SQLException {
        validarDias(dias);
        LocalDate hoy = LocalDate.now();
        return recordatorioDAO.listarProximos(hoy, hoy.plusDays(dias)).stream()
                .map(r -> Alerta.desde(r, hoy))
                .toList();
    }

    /** Alertas (vencidas + próximas) de un vehículo. */
    public List<Alerta> listarAlertasPorVehiculo(int vehiculoId, int dias) throws SQLException {
        validarId(vehiculoId, "El vehículo");
        validarDias(dias);
        LocalDate hoy = LocalDate.now();
        return recordatorioDAO.listarAlertasPorVehiculo(vehiculoId, hoy.plusDays(dias)).stream()
                .map(r -> Alerta.desde(r, hoy))
                .toList();
    }

    /** Marca como notificado solo una vez: evita notificaciones duplicadas. */
    public boolean notificar(int id) throws SQLException {
        validarId(id, "El ID");
        Recordatorio r = recordatorioDAO.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el recordatorio " + id));
        if (r.isNotificado()) {
            throw new IllegalStateException("El recordatorio " + id + " ya fue notificado");
        }
        return recordatorioDAO.marcarNotificado(id);
    }

    // ---------- validaciones ----------

    private void validarId(int id, String campo) {
        if (id <= 0) {
            throw new IllegalArgumentException(campo + " debe ser un número positivo");
        }
    }

    private void validarDias(int dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Los días no pueden ser negativos");
        }
    }

    private String descripcionOpcional(String descripcion) {
        if (descripcion == null || descripcion.trim().isEmpty()) {
            return null;
        }
        String limpia = descripcion.trim();
        if (limpia.length() > MAX_DESCRIPCION) {
            throw new IllegalArgumentException(
                    "La descripción no puede superar " + MAX_DESCRIPCION + " caracteres");
        }
        return limpia;
    }
}