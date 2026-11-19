package com.carmotors.controlador;

import com.carmotors.dao.FacturaDAO;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.ServicioFacturable;
import com.carmotors.servicios.facturacion.FacturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FacturaControladorTest {

    static class FakeFacturaDAO extends FacturaDAO {
        final List<Factura> facturas = new ArrayList<>();

        @Override
        public Optional<ServicioFacturable> buscarServicioFacturable(int id) {
            if (id != 1) return Optional.empty();
            return Optional.of(new ServicioFacturable(1, "Completado", new BigDecimal("40.00"),
                    "Ana", "1001", "ABC123 - Mazda 3",
                    List.of(new DetalleFactura(1, "Aceite", 2, new BigDecimal("10.00")))));
        }

        @Override
        public Optional<Factura> buscarPorServicio(int servicioId) {
            return facturas.stream().filter(f -> f.getServicioId() == servicioId).findFirst();
        }

        @Override
        public Factura crear(Factura f) {
            f.setId(facturas.size() + 1);
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

    private FacturaControlador controlador;

    @BeforeEach
    void setUp() {
        controlador = new FacturaControlador(
                new FacturaService(new FakeFacturaDAO(), BigDecimal.ZERO));
    }

    @Test
    void crear_valido_devuelveFacturaConTotal() throws SQLException {
        Factura f = controlador.crear(1);
        assertEquals(1, f.getId());
        assertEquals(new BigDecimal("60.00"), f.getTotalFinal());
    }

    @Test
    void crear_servicioInexistente_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.crear(9));
    }

    @Test
    void crear_duplicada_lanzaExcepcion() throws SQLException {
        controlador.crear(1);
        assertThrows(IllegalStateException.class, () -> controlador.crear(1));
    }

    @Test
    void buscarPorId_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.buscarPorId(0));
    }

    @Test
    void buscarPorId_existente_loEncuentra() throws SQLException {
        Factura f = controlador.crear(1);
        assertTrue(controlador.buscarPorId(f.getId()).isPresent());
    }

    @Test
    void listar_vacia_devuelveVacia() throws SQLException {
        assertTrue(controlador.listar().isEmpty());
    }

    @Test
    void listar_devuelveLasCreadas() throws SQLException {
        controlador.crear(1);
        assertEquals(1, controlador.listar().size());
    }

    @Test
    void constructor_servicioNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new FacturaControlador(null));
    }
}