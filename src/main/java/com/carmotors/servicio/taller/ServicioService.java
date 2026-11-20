package com.carmotors.servicio.taller;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.FuenteConexion;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicio.taller.dominio.IdentificadorServicio;
import com.carmotors.servicio.taller.dominio.IdentificadorVehiculo;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.InventarioServiciosPort;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import com.carmotors.servicios.inventario.InventarioServiciosJdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class ServicioService {
    private final ServicioDAO servicios;
    private final TecnicoDAO tecnicos;
    private final FuenteConexion fuente;
    private final ConsultaOrdenServicio<IdentificadorServicio, Servicio> consulta;

    public ServicioService() {
        this(new ServicioDAO(), new TecnicoDAO());
    }

    public ServicioService(ServicioDAO servicios, TecnicoDAO tecnicos) {
        this(servicios, tecnicos, ConexionFactory::obtenerConexion);
    }

    public ServicioService(ServicioDAO servicios, TecnicoDAO tecnicos, FuenteConexion fuente) {
        this.servicios = Objects.requireNonNull(servicios);
        this.tecnicos = Objects.requireNonNull(tecnicos);
        this.fuente = Objects.requireNonNull(fuente);
        this.consulta = new ConsultaOrdenServicio<>(servicios);
    }

    public IdentificadorServicio registrarServicio(Servicio solicitud) throws SQLException {
        validarAlta(solicitud);
        return servicios.registrar(solicitud);
    }

    public IdentificadorServicio registrarServicioConReservas(Servicio solicitud, List<RepuestoSolicitado> repuestos)
            throws SQLException {
        validarAlta(solicitud);
        List<RepuestoSolicitado> lineas = List.copyOf(repuestos).stream()
                .sorted(Comparator.comparingInt(RepuestoSolicitado::repuestoId)).toList();
        return enTransaccion((conexion, inventario) -> {
            if (!lineas.isEmpty()) {
                validarOrdenOperativa(nombreEstado(conexion, solicitud.estadoId()));
            }
            IdentificadorServicio id = servicios.registrar(conexion, solicitud);
            for (RepuestoSolicitado linea : lineas) {
                inventario.reservar(new ReservaRepuesto(Math.toIntExact(id.valor()),
                        linea.repuestoId(), linea.cantidad()));
            }
            return id;
        });
    }

    public record RepuestoSolicitado(int repuestoId, int cantidad) {
        public RepuestoSolicitado {
            validarId(repuestoId, "repuesto");
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser positiva");
            }
        }
    }

    private static void validarAlta(Servicio solicitud) {
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
        enTransaccion((conexion, inventario) -> {
            bloquearOrden(conexion, servicioId);
            String destino = nombreEstado(conexion, estadoId);
            // Al cancelar o completar, lo reservado que no se usó vuelve al stock;
            // lo ya usado no cambia (la factura cobra solo lo usado).
            if (cancelado(destino) || completado(destino)) {
                inventario.liberarTodo(id);
            }
            servicios.actualizarEstado(conexion, servicioId, estadoId);
            return null;
        });
    }

    public void reservarRepuesto(ReservaRepuesto solicitud) throws SQLException {
        moverRepuesto(solicitud.servicioId(), false, inventario -> inventario.reservar(solicitud));
    }

    public void consumirRepuesto(ConsumoRepuesto solicitud) throws SQLException {
        moverRepuesto(solicitud.servicioId(), true, inventario -> inventario.consumir(solicitud));
    }

    public void liberarRepuesto(ReservaRepuesto solicitud) throws SQLException {
        moverRepuesto(solicitud.servicioId(), false, inventario -> inventario.liberar(solicitud));
    }

    private void moverRepuesto(int id, boolean consumo, Consumer<InventarioServiciosPort> movimiento)
            throws SQLException {
        enTransaccion((conexion, inventario) -> {
            var orden = new IdentificadorServicio(id);
            int estadoActual = bloquearOrden(conexion, orden);
            validarOrdenOperativa(nombreEstado(conexion, estadoActual));
            if (consumo && servicios.tieneFactura(conexion, orden)) {
                throw new IllegalStateException("La orden ya está facturada. No se puede modificar su consumo.");
            }
            movimiento.accept(inventario);
            return null;
        });
    }

    private int bloquearOrden(Connection conexion, IdentificadorServicio id) throws SQLException {
        return servicios.bloquearEstadoActual(conexion, id)
                .orElseThrow(() -> servicioInexistente(Math.toIntExact(id.valor())));
    }

    private String nombreEstado(Connection conexion, int estadoId) throws SQLException {
        return servicios.buscarNombreEstado(conexion, estadoId)
                .orElseThrow(() -> new NoSuchElementException("No existe el estado " + estadoId));
    }

    private static boolean cancelado(String estado) {
        return "Cancelado".equalsIgnoreCase(estado.strip()) || "Cancelada".equalsIgnoreCase(estado.strip());
    }

    private static boolean completado(String estado) {
        return "Completado".equalsIgnoreCase(estado.strip()) || "Completada".equalsIgnoreCase(estado.strip());
    }

    private static void validarOrdenOperativa(String estado) {
        if (cancelado(estado)) {
            throw new IllegalStateException("La orden está cancelada");
        }
    }

    private <T> T enTransaccion(OperacionInventario<T> operacion) throws SQLException {
        try (Connection conexion = fuente.obtener()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = operacion.ejecutar(conexion, new InventarioServiciosJdbc(conexion));
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException | Error error) {
                try {
                    conexion.rollback();
                } catch (SQLException falloRollback) {
                    error.addSuppressed(falloRollback);
                }
                throw error;
            }
        }
    }

    @FunctionalInterface
    private interface OperacionInventario<T> {
        T ejecutar(Connection conexion, InventarioServiciosJdbc inventario) throws SQLException;
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

    public Optional<Tecnico> buscarTecnico(int id) throws SQLException {
        validarId(id, "técnico");
        return tecnicos.buscarPorId(id);
    }

    public List<Tecnico> listarTecnicos() throws SQLException {
        return tecnicos.listar();
    }

    public List<Tecnico> listarTecnicosActivos() throws SQLException {
        return tecnicos.listarActivos();
    }

    public boolean actualizarTecnicoActivo(int id, boolean activo) throws SQLException {
        validarId(id, "técnico");
        return tecnicos.actualizarActivo(id, activo);
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
