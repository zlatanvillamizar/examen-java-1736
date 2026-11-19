package com.carmotors.servicios.inventario;

import com.carmotors.dao.FuenteConexion;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.dao.Transaccion;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.InventarioServiciosPort;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Implementación JDBC de {@link InventarioServiciosPort}: Inventario gestiona los
 * movimientos de stock y valida las cantidades de {@code servicios_repuestos} dentro
 * de la transacción que inicia y coordina Servicios.
 *
 * <h2>Reglas (acordadas en el issue #53)</h2>
 * <ul>
 *   <li><b>Reservar</b> descuenta {@code repuestos.cantidad_stock} (una sola vez) y suma
 *       al saldo {@code cantidad_reservada}. La línea se crea con {@code precio_venta}
 *       vigente; reservas posteriores conservan ese precio.</li>
 *   <li><b>Consumir</b> pasa unidades del saldo reservado a {@code cantidad_usada}; no
 *       vuelve a descontar stock y no puede superar el saldo pendiente.</li>
 *   <li><b>Liberar</b> devuelve al stock únicamente unidades del saldo pendiente;
 *       {@code cantidad_usada} no cambia.</li>
 * </ul>
 *
 * <h2>Transacción</h2>
 * <p>Se crea con la {@link Connection} de la transacción de Servicios, con
 * {@code autoCommit} desactivado. No confirma, no deshace y no cierra la conexión.
 * <b>Si una operación lanza cualquier excepción, quien coordina debe hacer
 * {@code rollback}</b> de toda la transacción. {@link #ejecutarEnTransaccion} lo hace
 * automáticamente.</p>
 *
 * <h2>Concurrencia</h2>
 * <p>Cada operación bloquea primero la fila de {@code repuestos} y después la línea de
 * {@code servicios_repuestos}, siempre en ese orden. Si Servicios combina varias
 * operaciones de distintos repuestos en una misma transacción, conviene hacerlas en
 * orden ascendente de {@code repuesto_id}. MySQL resuelve un interbloqueo eventual
 * abortando una transacción (la {@link SQLException} llega como causa), que se puede
 * reintentar completa. Antes de cancelar, Servicios debería bloquear la fila del
 * servicio para que no entren reservas nuevas mientras se liberan.</p>
 *
 * <h2>Errores</h2>
 * <ul>
 *   <li>{@link StockInsuficienteException}: no hay stock para reservar.</li>
 *   <li>{@link RepuestoNoEncontradoException}: el repuesto no existe.</li>
 *   <li>{@link NoSuchElementException}: el servicio no tiene reserva de ese repuesto.</li>
 *   <li>{@link IllegalStateException}: la cantidad supera el saldo reservado pendiente,
 *       la conexión está en {@code autoCommit}, o falló JDBC (la {@link SQLException}
 *       queda como causa, porque el puerto no declara excepciones comprobadas).</li>
 * </ul>
 */
public final class InventarioServiciosJdbc implements InventarioServiciosPort {

    /** Operación de negocio que usa la conexión de la transacción y el puerto de Inventario. */
    @FunctionalInterface
    public interface OperacionConInventario<T> {
        T ejecutar(Connection conexion, InventarioServiciosJdbc inventario) throws SQLException;
    }

    private final Connection conexion;
    private final RepuestoDAO repuestos;
    private final ReservaRepuestoDAO reservas;

    public InventarioServiciosJdbc(Connection conexion) {
        this(conexion, new RepuestoDAO(), new ReservaRepuestoDAO());
    }

    InventarioServiciosJdbc(Connection conexion, RepuestoDAO repuestos, ReservaRepuestoDAO reservas) {
        this.conexion = Objects.requireNonNull(conexion, "La conexión de la transacción es obligatoria");
        this.repuestos = Objects.requireNonNull(repuestos);
        this.reservas = Objects.requireNonNull(reservas);
    }

    /**
     * Ejecuta {@code operacion} en una transacción nueva: confirma si termina bien y
     * hace {@code rollback} completo ante cualquier excepción. Pensado para que
     * Servicios coordine en una sola transacción sus propios cambios y los de Inventario.
     */
    public static <T> T ejecutarEnTransaccion(FuenteConexion fuente, OperacionConInventario<T> operacion)
            throws SQLException {
        Objects.requireNonNull(operacion, "La operación es obligatoria");
        return Transaccion.ejecutar(fuente, conexion ->
                operacion.ejecutar(conexion, new InventarioServiciosJdbc(conexion)));
    }

    @Override
    public void reservar(ReservaRepuesto solicitud) {
        Objects.requireNonNull(solicitud, "La reserva es obligatoria");
        enLaTransaccion("reservar", solicitud.servicioId(), solicitud.repuestoId(), () -> {
            // 1) Descuento único y atómico del stock (bloquea la fila de repuestos).
            repuestos.restarStock(conexion, solicitud.repuestoId(), solicitud.cantidad());
            // 2) Saldo reservado de la línea; la FK a servicios rechaza servicios inexistentes.
            if (!reservas.sumarReserva(conexion, solicitud.servicioId(), solicitud.repuestoId(),
                    solicitud.cantidad())) {
                throw new RepuestoNoEncontradoException(solicitud.repuestoId());
            }
        });
    }

    @Override
    public void consumir(ConsumoRepuesto solicitud) {
        Objects.requireNonNull(solicitud, "El consumo es obligatorio");
        enLaTransaccion("consumir", solicitud.servicioId(), solicitud.repuestoId(), () -> {
            // Mismo orden de bloqueo que reservar y liberar: primero repuestos, luego la línea.
            repuestos.bloquearParaActualizar(conexion, solicitud.repuestoId());
            // El stock ya se descontó al reservar: solo se mueve el saldo a usadas.
            if (!reservas.consumir(conexion, solicitud.servicioId(), solicitud.repuestoId(),
                    solicitud.cantidad())) {
                throw saldoInsuficiente(solicitud.servicioId(), solicitud.repuestoId(), solicitud.cantidad());
            }
        });
    }

    @Override
    public void liberar(ReservaRepuesto solicitud) {
        Objects.requireNonNull(solicitud, "La liberación es obligatoria");
        enLaTransaccion("liberar", solicitud.servicioId(), solicitud.repuestoId(), () ->
                liberarUnidades(solicitud.servicioId(), solicitud.repuestoId(), solicitud.cantidad()));
    }

    /**
     * Cancelación: libera todo el saldo reservado pendiente de cada repuesto del servicio
     * y lo devuelve al stock. {@code cantidad_usada} no cambia.
     *
     * @return unidades devueltas al stock
     */
    public int liberarTodo(int servicioId) {
        if (servicioId <= 0) {
            throw new IllegalArgumentException("El identificador de servicio debe ser positivo");
        }
        int[] devueltas = {0};
        enLaTransaccion("cancelar las reservas", servicioId, 0, () -> {
            for (ReservaRepuestoDAO.Linea linea : reservas.listarPorServicio(conexion, servicioId)) {
                repuestos.bloquearParaActualizar(conexion, linea.repuestoId());
                int pendiente = reservas.buscarParaActualizar(conexion, servicioId, linea.repuestoId())
                        .map(ReservaRepuestoDAO.Linea::cantidadReservada)
                        .orElse(0);
                if (pendiente > 0) {
                    liberarUnidades(servicioId, linea.repuestoId(), pendiente);
                    devueltas[0] += pendiente;
                }
            }
        });
        return devueltas[0];
    }

    // ---------- internos ----------

    /** Bloquea primero {@code repuestos} y luego la línea, para un orden de bloqueo fijo. */
    private void liberarUnidades(int servicioId, int repuestoId, int cantidad) throws SQLException {
        repuestos.bloquearParaActualizar(conexion, repuestoId);
        if (!reservas.liberar(conexion, servicioId, repuestoId, cantidad)) {
            throw saldoInsuficiente(servicioId, repuestoId, cantidad);
        }
        repuestos.sumarStock(conexion, repuestoId, cantidad);
    }

    private RuntimeException saldoInsuficiente(int servicioId, int repuestoId, int cantidad) throws SQLException {
        return reservas.buscar(conexion, servicioId, repuestoId)
                .<RuntimeException>map(linea -> new IllegalStateException(
                        "La cantidad " + cantidad + " supera la reserva pendiente (" + linea.cantidadReservada()
                                + ") del repuesto " + repuestoId + " en el servicio " + servicioId))
                .orElseGet(() -> new NoSuchElementException(
                        "El servicio " + servicioId + " no tiene reserva del repuesto " + repuestoId));
    }

    @FunctionalInterface
    private interface Paso {
        void ejecutar() throws SQLException;
    }

    private void enLaTransaccion(String accion, int servicioId, int repuestoId, Paso paso) {
        try {
            if (conexion.getAutoCommit()) {
                throw new IllegalStateException(
                        "Las operaciones de Inventario requieren una transacción (autoCommit desactivado)");
            }
            paso.ejecutar();
        } catch (SQLException e) {
            throw new IllegalStateException("Error de base de datos al " + accion
                    + (repuestoId > 0 ? " el repuesto " + repuestoId : "")
                    + " del servicio " + servicioId + "; se debe hacer rollback", e);
        }
    }
}
