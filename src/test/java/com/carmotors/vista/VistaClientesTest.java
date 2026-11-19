package com.carmotors.vista;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.modelo.Cliente;
import com.carmotors.servicios.clientes.ClienteService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VistaClientesTest {
    @Test
    void registraYPresentaElIdDevueltoPorElControlador() {
        var controlador = new ClienteControlador() {
            @Override
            public Cliente registrar(String nombre, String documento, String telefono, String email) {
                assertEquals("Ana", nombre);
                assertEquals("DOC-1", documento);
                assertEquals("3001234567", telefono);
                assertEquals("ana@example.test", email);
                var cliente = new Cliente(nombre, documento, telefono, email);
                cliente.setId(42);
                return cliente;
            }
        };
        try (var entrada = new Scanner("1\nAna\nDOC-1\n3001234567\nana@example.test\n0\nSIGUIENTE")) {
            assertTrue(ejecutar(entrada, controlador).contains("Cliente registrado con ID 42."));
            assertEquals("SIGUIENTE", entrada.nextLine());
        }
    }

    @Test
    void validaFormatoYRestauraElMenuSinAccederAJdbc() {
        try (var entrada = new Scanner("x\n2\nno-numero\n2\n0\n1\nAna\nDOC-1\n\ncorreo-invalido\n0\nSIGUIENTE")) {
            String salida = ejecutar(entrada, sinJdbc());
            assertTrue(salida.contains("Opción inválida."));
            assertTrue(salida.contains("ID del cliente debe ser un número entero."));
            assertTrue(salida.contains("ID del cliente debe ser positivo."));
            assertTrue(salida.contains("El email no tiene un formato válido"));
            assertEquals("SIGUIENTE", entrada.nextLine());
        }
    }

    @Test
    void recuperaLaConsolaTrasErroresJdbcYNoExponeDetalles() {
        var controlador = new ClienteControlador() {
            @Override
            public Cliente registrar(String nombre, String documento, String telefono, String email)
                    throws SQLException {
                throw new SQLIntegrityConstraintViolationException("Detalle interno de la restricción");
            }

            @Override
            public List<Cliente> listar() throws SQLException {
                throw new SQLException("Detalle interno de la conexión");
            }
        };
        try (var entrada = new Scanner("1\nAna\nDOC-1\n\nana@example.test\n4\n0\nSIGUIENTE")) {
            String salida = ejecutar(entrada, controlador);
            assertTrue(salida.contains("La base de datos rechazó el cambio."));
            assertTrue(salida.contains("Error de base de datos."));
            assertFalse(salida.contains("Detalle interno"));
            assertEquals("SIGUIENTE", entrada.nextLine());
        }
    }

    @Test
    void finDeEntradaDuranteLaActualizacionNoGuardaDatos() {
        try (var entrada = new Scanner("5\n1\n3001234567\n")) {
            String salida = ejecutar(entrada, sinJdbc());
            assertTrue(salida.contains("Correo: "));
            assertFalse(salida.contains("Contacto del cliente actualizado."));
        }
    }

    private static ClienteControlador sinJdbc() {
        return new ClienteControlador(new ClienteService(new ClienteDAO(() -> {
            throw new AssertionError("La operación no debe solicitar una conexión");
        })));
    }

    private static String ejecutar(Scanner entrada, ClienteControlador controlador) {
        var captura = new ByteArrayOutputStream();
        try (var salida = new PrintStream(captura, true, StandardCharsets.UTF_8)) {
            new VistaClientes(entrada, salida, controlador).mostrar();
        }
        return captura.toString(StandardCharsets.UTF_8);
    }
}
