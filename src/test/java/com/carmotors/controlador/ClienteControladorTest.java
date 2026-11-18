package com.carmotors.controlador;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.modelo.Cliente;
import com.carmotors.servicios.clientes.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClienteControladorTest {

    static class FakeClienteDAO extends ClienteDAO {
        final List<Cliente> datos = new ArrayList<>();
        int siguienteId = 1;

        @Override
        public Cliente registrar(Cliente c) {
            c.setId(siguienteId++);
            datos.add(c);
            return c;
        }

        @Override
        public Optional<Cliente> buscarPorId(int id) {
            return datos.stream().filter(c -> c.getId() == id).findFirst();
        }

        @Override
        public Optional<Cliente> buscarPorDocumento(String doc) {
            return datos.stream().filter(c -> c.getDocumentoIdentidad().equals(doc)).findFirst();
        }

        @Override
        public List<Cliente> listar() {
            return new ArrayList<>(datos);
        }

        @Override
        public boolean actualizarContacto(int id, String tel, String email) {
            return buscarPorId(id).map(c -> {
                c.setTelefono(tel);
                c.setEmail(email);
                return true;
            }).orElse(false);
        }
    }

    private ClienteControlador controlador;

    @BeforeEach
    void setUp() {
        controlador = new ClienteControlador(new ClienteService(new FakeClienteDAO()));
    }

    @Test
    void registrar_valido_devuelveClienteConId() throws SQLException {
        Cliente c = controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        assertEquals(1, c.getId());
        assertEquals("ana@mail.com", c.getEmail());
    }

    @Test
    void registrar_documentoDuplicado_lanzaExcepcion() throws SQLException {
        controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        assertThrows(IllegalStateException.class,
                () -> controlador.registrar("Luis", "1001", "301", "luis@mail.com"));
    }

    @Test
    void registrar_correoInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrar("Ana", "1001", "300", "sin-arroba"));
    }

    @Test
    void buscarPorId_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> controlador.buscarPorId(0));
    }

    @Test
    void buscarPorId_existente_loEncuentra() throws SQLException {
        Cliente c = controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        assertTrue(controlador.buscarPorId(c.getId()).isPresent());
    }

    @Test
    void buscarPorDocumento_existente_loEncuentra() throws SQLException {
        controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        assertTrue(controlador.buscarPorDocumento("1001").isPresent());
    }

    @Test
    void listar_devuelveLosRegistrados() throws SQLException {
        controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        controlador.registrar("Luis", "1002", "301", "luis@mail.com");
        assertEquals(2, controlador.listar().size());
    }

    @Test
    void actualizarContacto_valido_actualiza() throws SQLException {
        Cliente c = controlador.registrar("Ana", "1001", "300", "ana@mail.com");
        assertTrue(controlador.actualizarContacto(c.getId(), "3109999999", "nuevo@mail.com"));
        assertEquals("nuevo@mail.com", controlador.buscarPorId(c.getId()).orElseThrow().getEmail());
    }

    @Test
    void constructor_servicioNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteControlador(null));
    }
}