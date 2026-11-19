package com.carmotors.servicios.recordatorios;

import com.carmotors.dao.RecordatorioDAO;
import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Recordatorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecordatorioServiceTest {

    static class FakeRecordatorioDAO extends RecordatorioDAO {
        final List<Recordatorio> datos = new ArrayList<>();
        int siguienteId = 1;
        int llamadasCrear = 0;

        /** Inserta directo (sin validar fecha) para poder simular vencidos. */
        Recordatorio agregar(int vehiculoId, LocalDate fecha, boolean notificado) {
            Recordatorio r = new Recordatorio(vehiculoId, null, fecha, "x");
            r.setNotificado(notificado);
            r.setId(siguienteId++);
            datos.add(r);
            return r;
        }

        @Override
        public Recordatorio crear(Recordatorio r) {
            llamadasCrear++;
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
                    .sorted(Comparator.comparing(Recordatorio::getFechaProximoMantenimiento))
                    .toList();
        }

        @Override
        public List<Recordatorio> listarProximos(LocalDate desde, LocalDate hasta) {
            return datos.stream()
                    .filter(r -> !r.isNotificado())
                    .filter(r -> !r.getFechaProximoMantenimiento().isBefore(desde))
                    .filter(r -> !r.getFechaProximoMantenimiento().isAfter(hasta))
                    .sorted(Comparator.comparing(Recordatorio::getFechaProximoMantenimiento))
                    .toList();
        }

        @Override
        public List<Recordatorio> listarAlertasPorVehiculo(int vehiculoId, LocalDate limite) {
            return datos.stream()
                    .filter(r -> r.getVehiculoId() == vehiculoId)
                    .filter(r -> !r.isNotificado())
                    .filter(r -> !r.getFechaProximoMantenimiento().isAfter(limite))
                    .sorted(Comparator.comparing(Recordatorio::getFechaProximoMantenimiento))
                    .toList();
        }
    }

    private FakeRecordatorioDAO dao;
    private RecordatorioService service;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void setUp() {
        dao = new FakeRecordatorioDAO();
        service = new RecordatorioService(dao);
    }

    // ================= Recordatorios =================

    @Test
    void crear_vehiculoInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(0, null, LocalDate.now().plusDays(5), "Aceite"));
        assertEquals(0, dao.llamadasCrear);
    }

    @Test
    void crear_servicioOrigenInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(1, -3, LocalDate.now().plusDays(5), "Aceite"));
    }

    @Test
    void crear_fechaNula_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(1, null, null, "Aceite"));
    }

    @Test
    void crear_fechaPasada_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(1, null, LocalDate.now().minusDays(1), "Aceite"));
    }

    @Test
    void crear_descripcionMuyLarga_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(1, null, LocalDate.now().plusDays(5), "x".repeat(256)));
    }

    @Test
    void crear_valido_guardaNoNotificado() throws SQLException {
        Recordatorio r = service.crear(1, null, LocalDate.now().plusDays(30), "  Cambio de aceite ");
        assertEquals(1, r.getId());
        assertEquals("Cambio de aceite", r.getDescripcion());
        assertFalse(r.isNotificado());
        assertNull(r.getServicioOrigenId());
    }

    @Test
    void crear_descripcionVacia_seGuardaComoNull() throws SQLException {
        Recordatorio r = service.crear(1, 7, LocalDate.now().plusDays(1), "   ");
        assertNull(r.getDescripcion());
        assertEquals(7, r.getServicioOrigenId());
    }

    @Test
    void buscarPorId_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(0));
    }

    @Test
    void marcarNotificado_existente_loMarca() throws SQLException {
        Recordatorio r = service.crear(1, null, LocalDate.now().plusDays(10), "Frenos");
        assertTrue(service.marcarNotificado(r.getId()));
        assertTrue(service.buscarPorId(r.getId()).orElseThrow().isNotificado());
        assertTrue(service.listarPendientes().isEmpty());
    }

    @Test
    void marcarNotificado_idInexistente_devuelveFalse() throws SQLException {
        assertFalse(service.marcarNotificado(99));
    }

    @Test
    void marcarNotificado_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.marcarNotificado(-1));
    }

    @Test
    void listarProximos_filtraPorDias() throws SQLException {
        service.crear(1, null, LocalDate.now().plusDays(3), "Cercano");
        service.crear(1, null, LocalDate.now().plusDays(60), "Lejano");
        List<Recordatorio> proximos = service.listarProximos(7);
        assertEquals(1, proximos.size());
        assertEquals("Cercano", proximos.get(0).getDescripcion());
    }

    @Test
    void listarProximos_diasNegativos_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.listarProximos(-1));
    }

    @Test
    void listarPorVehiculo_devuelveSoloDeEseVehiculo() throws SQLException {
        service.crear(1, null, LocalDate.now().plusDays(5), "A");
        service.crear(2, null, LocalDate.now().plusDays(5), "B");
        assertEquals(1, service.listarPorVehiculo(1).size());
    }

    @Test
    void constructor_daoNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new RecordatorioService(null));
    }

    // ================= Alertas vencidas =================

    @Test
    void alertasVencidas_soloSinNotificarYFechaPasada() throws SQLException {
        dao.agregar(1, hoy.minusDays(3), false);
        dao.agregar(1, hoy.minusDays(5), true);
        dao.agregar(1, hoy.plusDays(2), false);
        List<Alerta> r = service.listarAlertasVencidas();
        assertEquals(1, r.size());
        assertEquals(Alerta.Tipo.VENCIDA, r.get(0).getTipo());
    }

    @Test
    void alertasVencidas_sinDatos_devuelveVacia() throws SQLException {
        assertTrue(service.listarAlertasVencidas().isEmpty());
    }

    @Test
    void alertasVencidas_ordenadasDeMasAntiguaAMasReciente() throws SQLException {
        dao.agregar(1, hoy.minusDays(1), false);
        dao.agregar(1, hoy.minusDays(9), false);
        List<Alerta> r = service.listarAlertasVencidas();
        assertEquals(-9, r.get(0).getDiasRestantes());
    }

    // ================= Alertas próximas =================

    @Test
    void alertasProximas_incluyeHoyYLimite_excluyeVencidasYLejanas() throws SQLException {
        dao.agregar(1, hoy.minusDays(1), false);
        dao.agregar(1, hoy, false);
        dao.agregar(1, hoy.plusDays(7), false);
        dao.agregar(1, hoy.plusDays(8), false);
        assertEquals(2, service.listarAlertasProximas(7).size());
    }

    @Test
    void alertasProximas_ignoraNotificadas() throws SQLException {
        dao.agregar(1, hoy.plusDays(2), true);
        assertTrue(service.listarAlertasProximas(7).isEmpty());
    }

    @Test
    void alertasProximas_sinDatos_devuelveVacia() throws SQLException {
        assertTrue(service.listarAlertasProximas(7).isEmpty());
    }

    @Test
    void alertasProximas_diasNegativos_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.listarAlertasProximas(-1));
    }

    // ================= Alertas por vehículo =================

    @Test
    void alertasPorVehiculo_filtraVehiculoYEstado() throws SQLException {
        dao.agregar(1, hoy.minusDays(2), false);
        dao.agregar(1, hoy.plusDays(3), false);
        dao.agregar(1, hoy.plusDays(3), true);
        dao.agregar(2, hoy.plusDays(3), false);
        assertEquals(2, service.listarAlertasPorVehiculo(1, 7).size());
    }

    @Test
    void alertasPorVehiculo_sinAlertas_devuelveVacia() throws SQLException {
        assertTrue(service.listarAlertasPorVehiculo(5, 7).isEmpty());
    }

    @Test
    void alertasPorVehiculo_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.listarAlertasPorVehiculo(0, 7));
    }

    @Test
    void alertasPorVehiculo_diasNegativos_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.listarAlertasPorVehiculo(1, -2));
    }

    // ================= Notificar =================

    @Test
    void notificar_marcaYLaSacaDeLasAlertas() throws SQLException {
        Recordatorio r = dao.agregar(1, hoy.plusDays(2), false);
        assertTrue(service.notificar(r.getId()));
        assertTrue(service.listarAlertasProximas(7).isEmpty());
    }

    @Test
    void notificar_segundaVez_lanzaExcepcion() throws SQLException {
        Recordatorio r = dao.agregar(1, hoy.plusDays(2), false);
        service.notificar(r.getId());
        assertThrows(IllegalStateException.class, () -> service.notificar(r.getId()));
    }

    @Test
    void notificar_inexistente_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.notificar(99));
    }

    @Test
    void notificar_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.notificar(0));
    }
}