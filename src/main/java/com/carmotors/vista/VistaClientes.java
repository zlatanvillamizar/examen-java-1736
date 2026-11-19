package com.carmotors.vista;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.modelo.Cliente;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

final class VistaClientes {
    private static final String MENSAJE_INTEGRIDAD =
            "La base de datos rechazó el cambio. Verifique que el documento y el correo no estén registrados.";

    private final EntradaConsola consola;
    private final PrintStream salida;
    private final ClienteControlador controlador;

    VistaClientes(Scanner entrada, PrintStream salida, ClienteControlador controlador) {
        this.consola = new EntradaConsola(entrada, salida);
        this.salida = salida;
        this.controlador = Objects.requireNonNull(controlador);
    }

    void mostrar() {
        while (true) {
            salida.println("""
                    === Clientes ===
                    1. Registrar cliente
                    2. Buscar cliente por ID
                    3. Buscar cliente por documento
                    4. Listar clientes
                    5. Actualizar contacto
                    0. Volver al menú principal""");
            try {
                switch (consola.leer("Opción")) {
                    case "0" -> { return; }
                    case "1" -> consola.ejecutar(this::registrar, MENSAJE_INTEGRIDAD);
                    case "2" -> consola.ejecutar(this::buscarPorId, MENSAJE_INTEGRIDAD);
                    case "3" -> consola.ejecutar(this::buscarPorDocumento, MENSAJE_INTEGRIDAD);
                    case "4" -> consola.ejecutar(this::listar, MENSAJE_INTEGRIDAD);
                    case "5" -> consola.ejecutar(this::actualizarContacto, MENSAJE_INTEGRIDAD);
                    default -> salida.println("Opción inválida. Elija un número entre 0 y 5.");
                }
            } catch (EntradaConsola.FinEntrada e) {
                return;
            }
        }
    }

    private void registrar() throws SQLException {
        String nombre = consola.obligatorio("Nombre");
        String documento = consola.obligatorio("Documento");
        String telefono = consola.opcional("Teléfono");
        String email = consola.obligatorio("Correo");
        Cliente cliente = controlador.registrar(nombre, documento, telefono, email);
        salida.println("Cliente registrado con ID " + cliente.getId() + ".");
    }

    private void buscarPorId() throws SQLException {
        int id = consola.enteroPositivo("ID del cliente");
        controlador.buscarPorId(id).ifPresentOrElse(this::mostrarCliente,
                () -> salida.println("No se encontró el cliente " + id + "."));
    }

    private void buscarPorDocumento() throws SQLException {
        String documento = consola.obligatorio("Documento");
        controlador.buscarPorDocumento(documento).ifPresentOrElse(this::mostrarCliente,
                () -> salida.println("No se encontró un cliente con ese documento."));
    }

    private void listar() throws SQLException {
        List<Cliente> clientes = controlador.listar();
        if (clientes.isEmpty()) {
            salida.println("No hay clientes registrados.");
        }
        clientes.forEach(this::mostrarCliente);
    }

    private void actualizarContacto() throws SQLException {
        int id = consola.enteroPositivo("ID del cliente");
        String telefono = consola.leer("Teléfono (Enter para dejar sin teléfono)");
        String email = consola.obligatorio("Correo");
        if (controlador.actualizarContacto(id, telefono, email)) {
            salida.println("Contacto del cliente actualizado.");
        } else {
            salida.println("No se encontró el cliente " + id + ".");
        }
    }

    private void mostrarCliente(Cliente cliente) {
        salida.printf("Cliente %d | %s | Documento: %s | Teléfono: %s | Correo: %s%n",
                cliente.getId(), cliente.getNombre(), cliente.getDocumentoIdentidad(),
                cliente.getTelefono() == null ? "sin registrar" : cliente.getTelefono(), cliente.getEmail());
    }
}
