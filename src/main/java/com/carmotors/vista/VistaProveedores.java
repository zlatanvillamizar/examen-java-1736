package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.modelo.Proveedor;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/**
 * Vista de consola de proveedores: registro, búsquedas, listado y actualización de los
 * datos de contacto (persona de contacto, teléfono y email). El nombre no se modifica:
 * es el dato único con el que se identifica al proveedor en {@code proveedores}.
 *
 * <p>Recibe el {@link Scanner} compartido del menú principal y no lo cierra.</p>
 */
final class VistaProveedores {

    private static final String MENSAJE_INTEGRIDAD =
            "La base de datos rechazó el proveedor: ya existe uno con ese nombre.";

    private final EntradaConsola consola;
    private final PrintStream salida;
    private final InventarioControlador controlador;

    VistaProveedores(Scanner entrada, PrintStream salida, InventarioControlador controlador) {
        this.consola = new EntradaConsola(entrada, salida);
        this.salida = Objects.requireNonNull(salida);
        this.controlador = Objects.requireNonNull(controlador, "El controlador de inventario es obligatorio");
    }

    void mostrar() {
        while (true) {
            salida.println("""
                    === Proveedores ===
                    1. Registrar proveedor
                    2. Buscar proveedor por ID
                    3. Buscar proveedor por nombre
                    4. Listar proveedores
                    5. Actualizar contacto de proveedor
                    0. Volver al menú principal""");
            try {
                String opcion = consola.leer("Opción");
                switch (opcion) {
                    case "0" -> {
                        return;
                    }
                    case "1" -> consola.ejecutar(this::registrar, MENSAJE_INTEGRIDAD);
                    case "2" -> consola.ejecutar(this::buscarPorId, MENSAJE_INTEGRIDAD);
                    case "3" -> consola.ejecutar(this::buscarPorNombre, MENSAJE_INTEGRIDAD);
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
        String contacto = consola.opcional("Persona de contacto");
        String telefono = consola.opcional("Teléfono");
        String email = consola.opcional("Email");
        Proveedor proveedor = controlador.registrarProveedor(nombre, contacto, telefono, email);
        salida.println("Proveedor registrado con ID " + proveedor.id() + ".");
    }

    private void buscarPorId() throws SQLException {
        int id = consola.enteroPositivo("ID del proveedor");
        controlador.buscarProveedor(id).ifPresentOrElse(this::mostrarProveedor,
                () -> salida.println("No se encontró el proveedor " + id + "."));
    }

    private void buscarPorNombre() throws SQLException {
        String nombre = consola.obligatorio("Nombre del proveedor");
        controlador.buscarProveedorPorNombre(nombre).ifPresentOrElse(this::mostrarProveedor,
                () -> salida.println("No se encontró un proveedor con el nombre " + nombre + "."));
    }

    private void listar() throws SQLException {
        List<Proveedor> proveedores = controlador.listarProveedores();
        if (proveedores.isEmpty()) {
            salida.println("No hay proveedores registrados.");
        }
        proveedores.forEach(this::mostrarProveedor);
    }

    private void actualizarContacto() throws SQLException {
        int id = consola.enteroPositivo("ID del proveedor");
        Optional<Proveedor> encontrado = controlador.buscarProveedor(id);
        if (encontrado.isEmpty()) {
            salida.println("No se encontró el proveedor " + id + ".");
            return;
        }
        Proveedor actual = encontrado.get();
        salida.println("Proveedor: " + actual.nombre() + " (el nombre no se modifica).");
        String contacto = consola.actualizado("Persona de contacto", actual.personaContacto());
        String telefono = consola.actualizado("Teléfono", actual.telefono());
        String email = consola.actualizado("Email", actual.email());
        if (controlador.actualizarContactoProveedor(id, contacto, telefono, email)) {
            salida.println("Contacto del proveedor actualizado.");
            controlador.buscarProveedor(id).ifPresent(this::mostrarProveedor);
        } else {
            salida.println("No se encontró el proveedor " + id + ".");
        }
    }

    private void mostrarProveedor(Proveedor proveedor) {
        salida.printf("Proveedor %d | %s | Contacto: %s | Teléfono: %s | Email: %s%n",
                proveedor.id(), proveedor.nombre(), texto(proveedor.personaContacto()),
                texto(proveedor.telefono()), texto(proveedor.email()));
    }

    private static String texto(String valor) {
        return valor == null ? "sin registrar" : valor;
    }
}
