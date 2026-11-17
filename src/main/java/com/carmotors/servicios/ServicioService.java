package com.carmotors.servicios;

import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicios.dominio.IdentificadorServicio;
import com.carmotors.servicios.dominio.IdentificadorVehiculo;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class ServicioService {
    private final ServicioDAO servicios;
    private final TecnicoDAO tecnicos;
    private final ConsultaOrdenServicio<IdentificadorServicio, Servicio> consulta;

    public ServicioService() {
        this(new ServicioDAO(), new TecnicoDAO());
    }

    public ServicioService(ServicioDAO servicios, TecnicoDAO tecnicos) {
        this.servicios = Objects.requireNonNull(servicios);
        this.tecnicos = Objects.requireNonNull(tecnicos);
        this.consulta = new ConsultaOrdenServicio<>(servicios);
    }

    public IdentificadorServicio registrarServicio(Servicio solicitud) throws SQLException {
        if (solicitud == null || solicitud.id() != null || solicitud.fechaRecepcion() == null) {
            throw new IllegalArgumentException("El alta requiere fecha de recepción y un ID por generar");
        }
        new IdentificadorVehiculo(solicitud.vehiculoId());
        validarId(solicitud.mantenimientoTipoId(), "tipo de mantenimiento");
        validarId(solicitud.estadoId(), "estado");
        if (solicitud.descripcionProblema() == null || solicitud.descripcionProblema().isBlank()) {
            throw new IllegalArgumentException("La descripción del problema es obligatoria");
        }
        if (solicitud.costoManoObra() != null && solicitud.costoManoObra().signum() < 0) {
            throw new IllegalArgumentException("El costo de mano de obra debe ser no negativo");
        }
        validarFechaEntrega(solicitud.fechaRecepcion(), solicitud.fechaEntrega());
        return servicios.registrar(solicitud);
    }

    public Optional<Servicio> buscarPorId(int id) {
        return consulta.buscarPorId(new IdentificadorServicio(id));
    }

    public List<Servicio> listar() {
        return consulta.listar();
    }

    public void actualizarEstado(int id, int estadoId) throws SQLException {
        IdentificadorServicio servicioId = new IdentificadorServicio(id);
        validarId(estadoId, "estado");
        if (!servicios.actualizarEstado(servicioId, estadoId)) {
            throw servicioInexistente(id);
        }
    }

    public void actualizarFechaEntrega(int id, LocalDateTime fechaEntrega) throws SQLException {
        Servicio servicio = buscarPorId(id).orElseThrow(() -> servicioInexistente(id));
        validarFechaEntrega(servicio.fechaRecepcion(), fechaEntrega);
        if (!servicios.actualizarFechaEntrega(new IdentificadorServicio(id), fechaEntrega)) {
            throw servicioInexistente(id);
        }
    }

    public int registrarTecnico(Tecnico solicitud) throws SQLException {
        if (solicitud == null || solicitud.id() != null
                || solicitud.nombre() == null || solicitud.nombre().isBlank()) {
            throw new IllegalArgumentException("El alta de un técnico requiere nombre y un ID por generar");
        }
        return tecnicos.registrar(new Tecnico(null, solicitud.nombre().strip(),
                solicitud.especialidad(), solicitud.activo()));
    }

    public void asignarTecnico(ServicioTecnico asignacion) throws SQLException {
        if (asignacion == null || asignacion.fechaAsignacion() == null) {
            throw new IllegalArgumentException("La fecha de asignación es obligatoria");
        }
        int servicioId = asignacion.servicioId();
        int tecnicoId = asignacion.tecnicoId();
        IdentificadorServicio id = new IdentificadorServicio(servicioId);
        validarId(tecnicoId, "técnico");
        Tecnico tecnico = tecnicos.buscarPorId(tecnicoId)
                .orElseThrow(() -> new NoSuchElementException("No existe el técnico " + tecnicoId));
        if (!tecnico.activo()) {
            throw new IllegalStateException("El técnico debe estar activo para asignarlo");
        }
        consulta.buscarPorId(id).orElseThrow(() -> servicioInexistente(servicioId));
        if (!servicios.asignarTecnico(asignacion)) {
            throw new IllegalStateException("El técnico dejó de estar disponible para la asignación");
        }
    }

    private static void validarId(int id, String entidad) {
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador de " + entidad + " debe ser positivo");
        }
    }

    private static void validarFechaEntrega(LocalDateTime recepcion, LocalDateTime entrega) {
        if (entrega != null && entrega.isBefore(recepcion)) {
            throw new IllegalArgumentException("La fecha de entrega es anterior a la recepción");
        }
    }

    private static NoSuchElementException servicioInexistente(int id) {
        return new NoSuchElementException("No existe el servicio " + id);
    }
}
