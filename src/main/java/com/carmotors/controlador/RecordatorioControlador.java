package com.carmotors.controlador;

import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Recordatorio;
import com.carmotors.servicios.recordatorios.RecordatorioService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class RecordatorioControlador {

    private final RecordatorioService service;

    public RecordatorioControlador() {
        this(new RecordatorioService());
    }

    public RecordatorioControlador(RecordatorioService service) {
        if (service == null) {
            throw new IllegalArgumentException("El servicio es obligatorio");
        }
        this.service = service;
    }

    public Recordatorio crear(int vehiculoId, Integer servicioOrigenId,
                              LocalDate fecha, String descripcion) throws SQLException {
        return service.crear(vehiculoId, servicioOrigenId, fecha, descripcion);
    }

    public Optional<Recordatorio> buscarPorId(int id) throws SQLException {
        return service.buscarPorId(id);
    }

    public List<Recordatorio> listarPendientes() throws SQLException {
        return service.listarPendientes();
    }

    public List<Recordatorio> listarProximos(int dias) throws SQLException {
        return service.listarProximos(dias);
    }

    public List<Recordatorio> listarPorVehiculo(int vehiculoId) throws SQLException {
        return service.listarPorVehiculo(vehiculoId);
    }

    public boolean marcarNotificado(int id) throws SQLException {
        return service.marcarNotificado(id);
    }

    // ---------- alertas ----------

    public List<Alerta> listarAlertasVencidas() throws SQLException {
        return service.listarAlertasVencidas();
    }

    public List<Alerta> listarAlertasProximas(int dias) throws SQLException {
        return service.listarAlertasProximas(dias);
    }

    public List<Alerta> listarAlertasPorVehiculo(int vehiculoId, int dias) throws SQLException {
        return service.listarAlertasPorVehiculo(vehiculoId, dias);
    }

    public boolean notificar(int id) throws SQLException {
        return service.notificar(id);
    }
}