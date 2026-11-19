package com.carmotors.controlador;

import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicio.taller.ServicioService;
import com.carmotors.servicio.taller.dominio.IdentificadorServicio;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Punto de entrada del módulo de Servicios para la futura vista.
 * No contiene SQL: valida/delega los casos de uso en ServicioService.
 */
public final class ServicioControlador {
    private final ServicioService servicio;

    public ServicioControlador() {
        this(new ServicioService());
    }

    public ServicioControlador(ServicioService servicio) {
        this.servicio = Objects.requireNonNull(servicio);
    }

    public IdentificadorServicio registrarServicio(Servicio solicitud) throws SQLException {
        return servicio.registrarServicio(solicitud);
    }

    public Optional<Servicio> buscarServicio(int id) {
        return servicio.buscarPorId(id);
    }

    public List<Servicio> listarServicios() {
        return servicio.listar();
    }

    public void actualizarEstado(int servicioId, int estadoId) throws SQLException {
        servicio.actualizarEstado(servicioId, estadoId);
    }

    public void reservarRepuesto(ReservaRepuesto solicitud) throws SQLException {
        servicio.reservarRepuesto(solicitud);
    }

    public void consumirRepuesto(ConsumoRepuesto solicitud) throws SQLException {
        servicio.consumirRepuesto(solicitud);
    }

    public void liberarRepuesto(ReservaRepuesto solicitud) throws SQLException {
        servicio.liberarRepuesto(solicitud);
    }

    public void actualizarFechaEntrega(int servicioId, LocalDateTime fechaEntrega) throws SQLException {
        servicio.actualizarFechaEntrega(servicioId, fechaEntrega);
    }

    public int registrarTecnico(Tecnico solicitud) throws SQLException {
        return servicio.registrarTecnico(solicitud);
    }

    public Optional<Tecnico> buscarTecnico(int id) throws SQLException {
        return servicio.buscarTecnico(id);
    }

    public List<Tecnico> listarTecnicos() throws SQLException {
        return servicio.listarTecnicos();
    }

    public List<Tecnico> listarTecnicosActivos() throws SQLException {
        return servicio.listarTecnicosActivos();
    }

    public boolean actualizarTecnicoActivo(int id, boolean activo) throws SQLException {
        return servicio.actualizarTecnicoActivo(id, activo);
    }

    public void asignarTecnico(ServicioTecnico asignacion) throws SQLException {
        servicio.asignarTecnico(asignacion);
    }
}
