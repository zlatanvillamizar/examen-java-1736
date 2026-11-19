package com.carmotors.servicios.clientes;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.modelo.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClienteServiceTest {

    static class FakeClienteDAO extends ClienteDAO {
        final List<Cliente> datos = new ArrayList<>();
        int siguienteId = 1;
        int llamadasRegistrar = 0;

        @Override
        public Cliente registrar(Cliente c) {
            llamadasRegistrar++;
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

    private FakeClienteDAO dao;
    private ClienteService service;

    @BeforeEach
    void setUp() {
        dao = new FakeClienteDAO();
        service = new ClienteService(dao);
    }

    @Test
    void registrar_nombreNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(null, "1001", "300", "a@b.com"));
        assertEquals(0, dao.llamadasRegistrar);
    }

    @Test
    void registrar_nombreVacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("   ", "1001", "300", "a@b.com"));
    }

    @Test
    void registrar_documentoVacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Ana", "", "300", "a@b.com"));
    }

    @Test
    void registrar_documentoMuyLargo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Ana", "1".repeat(21), "300", "a@b.com"));
    }

    @Test
    void registrar_emailVacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Ana", "1001", "300", " "));
    }

    @Test
    void registrar_emailSinArroba_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Ana", "1001", "300", "anacorreo.com"));
    }

    @Test
    void registrar_emailSinDominio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Ana", "1001", "300", "ana@correo"));
    }

    @Test
    void registrar_datosValidos_normalizaYGuarda() throws SQLException {
        Cliente c = service.registrar("  Ana Pérez ", " 1001 ", " 3001234567 ", "  ANA@Correo.COM ");
        assertEquals(1, c.getId());
        assertEquals("Ana Pérez", c.getNombre());
        assertEquals("1001", c.getDocumentoIdentidad());
        assertEquals("3001234567", c.getTelefono());
        assertEquals("ana@correo.com", c.getEmail());
    }

    @Test
    void registrar_telefonoVacio_seGuardaComoNull() throws SQLException {
        Cliente c = service.registrar("Ana", "1001", "  ", "a@b.com");
        assertNull(c.getTelefono());
    }

    @Test
    void registrar_documentoDuplicado_lanzaExcepcion() throws SQLException {
        service.registrar("Ana", "1001", "300", "a@b.com");
        assertThrows(IllegalStateException.class,
                () -> service.registrar("Luis", "1001", "301", "l@b.com"));
        assertEquals(1, dao.llamadasRegistrar);
    }

    @Test
    void buscarPorId_idCeroONegativo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(0));
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(-5));
    }

    @Test
    void buscarPorId_existente_devuelveCliente() throws SQLException {
        Cliente c = service.registrar("Ana", "1001", "300", "a@b.com");
        assertTrue(service.buscarPorId(c.getId()).isPresent());
    }

    @Test
    void buscarPorDocumento_vacio_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorDocumento("  "));
    }

    @Test
    void buscarPorDocumento_recortaEspacios() throws SQLException {
        service.registrar("Ana", "1001", "300", "a@b.com");
        assertTrue(service.buscarPorDocumento(" 1001 ").isPresent());
    }

    @Test
    void listar_devuelveClientesRegistrados() throws SQLException {
        service.registrar("Ana", "1001", "300", "a@b.com");
        service.registrar("Luis", "1002", "301", "l@b.com");
        assertEquals(2, service.listar().size());
    }

    @Test
    void actualizarContacto_idInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarContacto(0, "300", "a@b.com"));
    }

    @Test
    void actualizarContacto_emailInvalido_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarContacto(1, "300", "sin-arroba"));
    }

    @Test
    void actualizarContacto_valido_actualiza() throws SQLException {
        Cliente c = service.registrar("Ana", "1001", "300", "a@b.com");
        assertTrue(service.actualizarContacto(c.getId(), " 3109999999 ", "NUEVO@mail.com"));
        Cliente actualizado = service.buscarPorId(c.getId()).orElseThrow();
        assertEquals("3109999999", actualizado.getTelefono());
        assertEquals("nuevo@mail.com", actualizado.getEmail());
    }

    @Test
    void actualizarContacto_idInexistente_devuelveFalse() throws SQLException {
        assertFalse(service.actualizarContacto(99, "300", "a@b.com"));
    }

    @Test
    void constructor_daoNulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(null));
    }
}