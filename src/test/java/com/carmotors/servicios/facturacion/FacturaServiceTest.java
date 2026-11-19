package com.carmotors.servicios.facturacion;

import com.carmotors.dao.FacturaDAO;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.ServicioFacturable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FacturaServiceTest {

    static class FakeFacturaDAO extends FacturaDAO {
        final Map<Integer, ServicioFacturable> servicios = new HashMap<>();
        final List<Factura> facturas = new ArrayList<>();
        int siguienteId = 1;
        int llamadasCrear = 0;

        @Override
        public Optional<ServicioFacturable> buscarServicioFacturable(int id) {
            return Optional.ofNullable(servicios.get(id));
        }

        @Override
        public Optional<Factura> buscarPorServicio(int servicioId) {
            return facturas.stream().filter(f -> f.getServicioId() == servicioId).findFirst();
        }

        @Override
        public Factura crear(Factura f) {
            llamadasCrear++;
            f.setId(siguienteId++);
            facturas.add(f);
            return f;
        }

        @Override
        public Optional<Factura> buscarPorId(int id) {
            return facturas.stream().filter(f -> f.getId() == id).findFirst();
        }

        @Override
        public List<Factura> listar() {
            return new ArrayList<>(facturas);
        }
    }

    private FakeFacturaDAO dao;
    private FacturaService service;

    private ServicioFacturable servicio(int id, String estado, String manoObra,
                                        DetalleFactura... detalles) {
        ServicioFacturable s = new ServicioFacturable(id, estado,
                manoObra == null ? null : new BigDecimal(manoObra),
                "Ana", "1001", "ABC123 - Mazda 3", List.of(detalles));
        dao.servicios.put(id, s);
        return s;
    }

    @BeforeEach
    void setUp() {
        dao = new FakeFacturaDAO();
        service = new FacturaService(dao, new BigDecimal("0.10"));
    }

    // ---------- cálculos ----------

    @Test
    void calcula_subtotalManoObraImpuestosYTotal_conRedondeo() throws SQLException {
        servicio(1, "Completado", "100.00",
                new DetalleFactura(1, "Aceite", 2, new BigDecimal("10.00")),
                new DetalleFactura(2, "Filtro", 1, new BigDecimal("5.55")));
        Factura f = service.crear(1);
        assertEquals(new BigDecimal("25.55"), f.getTotalRepuestos());
        assertEquals(new BigDecimal("100.00"), f.getTotalManoObra());
        assertEquals(new BigDecimal("12.56"), f.getImpuestos());   // 12.555 -> HALF_UP
        assertEquals(new BigDecimal("138.11"), f.getTotalFinal());
    }

    @Test
    void sinRepuestos_totalRepuestosEsCero() throws SQLException {
        servicio(1, "Completado", "50.00");
        Factura f = service.crear(1);
        assertEquals(new BigDecimal("0.00"), f.getTotalRepuestos());
        assertEquals(new BigDecimal("55.00"), f.getTotalFinal());
    }

    @Test
    void manoObraNula_seTrataComoCero() throws SQLException {
        servicio(1, "Completado", null,
                new DetalleFactura(1, "Aceite", 1, new BigDecimal("10.00")));
        Factura f = service.crear(1);
        assertEquals(new BigDecimal("0.00"), f.getTotalManoObra());
        assertEquals(new BigDecimal("11.00"), f.getTotalFinal());
    }

    @Test
    void tasaCero_noAplicaImpuestos() throws SQLException {
        FacturaService sinImpuesto = new FacturaService(dao, BigDecimal.ZERO);
        servicio(1, "Completado", "100.00");
        Factura f = sinImpuesto.crear(1);
        assertEquals(new BigDecimal("0.00"), f.getImpuestos());
        assertEquals(new BigDecimal("100.00"), f.getTotalFinal());
    }

    @Test
    void sinErroresDePrecision_conDecimalesRepetidos() {
        ServicioFacturable s = new ServicioFacturable(1, "Completado", new BigDecimal("0.10"),
                "Ana", "1", "v",
                List.of(new DetalleFactura(1, "x", 3, new BigDecimal("0.10"))));
        Factura f = service.calcular(s);
        assertEquals(new BigDecimal("0.30"), f.getTotalRepuestos()); // con double daría 0.30000000000000004
    }

    @Test
    void manoObraNegativa_lanzaExcepcion() {
        ServicioFacturable s = new ServicioFacturable(1, "Completado", new BigDecimal("-1"),
                "Ana", "1", "v", List.of());
        assertThrows(IllegalArgumentException.class, () -> service.calcular(s));
    }

    // ---------- validaciones ----------

    @Test
    void crear_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(0));
        assertThrows(IllegalArgumentException.class, () -> service.crear(-4));
        assertEquals(0, dao.llamadasCrear);
    }

    @Test
    void crear_servicioInexistente_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(99));
        assertEquals(0, dao.llamadasCrear);
    }

    @Test
    void crear_servicioNoCompletado_lanzaExcepcion() {
        servicio(1, "Pendiente", "10.00");
        assertThrows(IllegalStateException.class, () -> service.crear(1));
        assertEquals(0, dao.llamadasCrear);
    }

    @Test
    void crear_servicioYaFacturado_lanzaExcepcion() throws SQLException {
        servicio(1, "Completado", "10.00");
        service.crear(1);
        assertThrows(IllegalStateException.class, () -> service.crear(1));
        assertEquals(1, dao.llamadasCrear);
    }

    @Test
    void crear_asignaIdYGuarda() throws SQLException {
        servicio(1, "Completado", "10.00");
        Factura f = service.crear(1);
        assertEquals(1, f.getId());
        assertEquals(1, dao.facturas.size());
        assertNotNull(f.getFechaEmision());
    }

    // ---------- consultas ----------

    @Test
    void buscarPorId_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(0));
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() throws SQLException {
        assertTrue(service.buscarPorId(5).isEmpty());
    }

    @Test
    void buscarPorId_existente_incluyeClienteYDetalles() throws SQLException {
        servicio(1, "Completado", "10.00",
                new DetalleFactura(1, "Aceite", 1, new BigDecimal("10.00")));
        Factura creada = service.crear(1);
        Factura f = service.buscarPorId(creada.getId()).orElseThrow();
        assertEquals("Ana", f.getClienteNombre());
        assertEquals(1, f.getDetalles().size());
    }

    @Test
    void listar_sinFacturas_devuelveVacia() throws SQLException {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listar_devuelveLasCreadas() throws SQLException {
        servicio(1, "Completado", "10.00");
        servicio(2, "Completado", "20.00");
        service.crear(1);
        service.crear(2);
        assertEquals(2, service.listar().size());
    }

    // ---------- constructor ----------

    @Test
    void constructor_daoNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new FacturaService(null, BigDecimal.ZERO));
    }

    @Test
    void constructor_tasaInvalida_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new FacturaService(dao, null));
        assertThrows(IllegalArgumentException.class,
                () -> new FacturaService(dao, new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class,
                () -> new FacturaService(dao, new BigDecimal("1.01")));
    }
}