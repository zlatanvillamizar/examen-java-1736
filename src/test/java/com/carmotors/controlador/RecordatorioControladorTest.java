package com.carmotors.controlador;

import com.carmotors.dao.RecordatorioDAO;
import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Recordatorio;
import com.carmotors.servicios.recordatorios.RecordatorioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecordatorioControladorTest {

    static class FakeRecordatorioDAO extends RecordatorioDAO {
        final List<Recordatorio> datos = new ArrayList<>();
        int siguienteId = 1;

        /** Inserta directo (sin validar fecha) para simular vencidos. */
        Recordatorio agregar(int vehiculoId, LocalDate fecha, boolean notificado) {
            Recordatorio r = new Recordatorio(vehiculoId, null, fecha, "x");
            r.setNotificado(notificado);
            r.setId(siguienteId++);
            datos.add(r);
            return r;
        }

        @Override
        public Recordatorio crear(Recordatorio r) {
            r.setId(siguienteId++);
            datos.add(r);
            return r;
        }

        @Override
        public Optional<Recordatorio> buscarPorId(int id) {
            return datos.stream().filter(r -> r.getId() == id).findFirst();
        }

        @Override
        public List<Recordatorio> listarPendientes() {
            return datos.stream().filter(r -> !r.isNotificado()).toList();
        }

        @Override
        public List<Recordatorio> listarPorVehiculo(int vehiculoId) {
            return datos.stream().filter(r -> r.getVehiculoId() == vehiculoId).toList();
        }

        @Override
        public boolean marcarNotificado(int id) {
            return buscarPorId(id).map(r -> {
                r.setNotificado(true);
                return true;
            }).orElse(false);
        }

        // ---------- alertas ----------

        @Override
        public List<Recordatorio> listarVencidos(LocalDate hoy) {
            return datos.stream()
                    .filter(r -> !r.isNotificado())
                    .filter(r -> r.getFechaProximoMantenimiento().isBefore(hoy))
                    .toList();
        }

        @Override
        public List<Recordatorio> listarProximos(LocalDate desde, LocalDate hasta) {
            return datos.stream()
                    .filter(r -> !r.isNotificado())
                    .filter(r -> !r.getFechaProximoMantenimiento().isBefore(desde))
                    .filter(r -> !r.getFechaProximoMantenimiento().isAfter(hasta))
                    .toList();
        }

        @Override
        public List<Recordatorio> listarAlertasPorVehiculo(int vehiculoId, LocalDate limite) {
            return datos.stream()
                    .filter(r -> r.getVehiculoId() == vehiculoId)
                    .filter(r -> !r.isNotificado())
                    .filter(r -> !r.getFechaProximoMantenimiento().isAfter(limite))
                    .toList();
        }
    }

    private FakeRecordatorioDAO dao;
    private RecordatorioControlador controlador;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void setUp() {
        dao = new FakeRecordatorioDAO();
        controlador = new RecordatorioControlador(new RecordatorioService(dao));
    }

    // ================= Recordatorios =================

    @Test
    void crear_valido_devuelveConId() throws SQLException {
        Recordatorio r = controlador.crear(1, null, LocalDate.now().plusDays(10), "Aceite");
        assertEquals(1, r.getId());
    }

    @Test
    void crear_fechaPasada_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.crear(1, null, LocalDate.now().minusDays(2), "Aceite"));
    }

    @Test
    void crear_vehiculoInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.crear(0, null, LocalDate.now().plusDays(2), "Aceite"));
    }

    @Test
    void listarPendientes_devuelveLosCreados() throws SQLException {
        controlador.crear(1, null, LocalDate.now().plusDays(5), "A");
        controlador.crear(2, null, LocalDate.now().plusDays(6), "B");
        assertEquals(2, controlador.listarPendientes().size());
    }

    @Test
    void marcarNotificado_quitaDePendientes() throws SQLException {
        Recordatorio r = controlador.crear(1, null, LocalDate.now().plusDays(5), "A");
        assertTrue(controlador.marcarNotificado(r.getId()));
        assertTrue(controlador.listarPendientes().isEmpty());
    }

    @Test
    void buscarPorId_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.buscarPorId(0));
    }

    @Test
    void constructor_servicioNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new RecordatorioControlador(null));
    }

    // ================= Alertas =================

    @Test
    void alertasVencidas_delegaAlServicio() throws SQLException {
        dao.agregar(1, hoy.minusDays(2), false);
        List<Alerta> r = controlador.listarAlertasVencidas();
        assertEquals(1, r.size());
        assertEquals(Alerta.Tipo.VENCIDA, r.get(0).getTipo());
    }

    @Test
    void alertasVencidas_sinDatos_devuelveVacia() throws SQLException {
        assertTrue(controlador.listarAlertasVencidas().isEmpty());
    }

    @Test
    void alertasProximas_delegaAlServicio() throws SQLException {
        dao.agregar(1, hoy.plusDays(3), false);
        assertEquals(1, controlador.listarAlertasProximas(7).size());
    }

    @Test
    void alertasProximas_diasNegativos_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.listarAlertasProximas(-1));
    }

    @Test
    void alertasPorVehiculo_consultaVacia_devuelveVacia() throws SQLException {
        assertTrue(controlador.listarAlertasPorVehiculo(9, 7).isEmpty());
    }

    @Test
    void alertasPorVehiculo_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.listarAlertasPorVehiculo(0, 7));
    }

    @Test
    void notificar_unaSolaVez() throws SQLException {
        Recordatorio r = dao.agregar(1, hoy.plusDays(2), false);
        assertTrue(controlador.notificar(r.getId()));
        assertThrows(IllegalStateException.class, () -> controlador.notificar(r.getId()));
    }

    @Test
    void notificar_inexistente_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.notificar(99));
    }
}