package com.carmotors.vista;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.modelo.Cliente;

import java.util.List;
import java.util.Scanner;

public class ClienteVista {

    private final ClienteControlador controlador;
    private final Scanner scanner;

    public ClienteVista() {
        this(new ClienteControlador(), new Scanner(System.in));
    }

    public ClienteVista(ClienteControlador controlador, Scanner scanner) {
        this.controlador = controlador;
        this.scanner = scanner;
    }

    public void mostrarMenu() {
        int opcion;
        do {
            System.out.println("\n=== GESTIÓN DE CLIENTES ===");
            System.out.println("1. Registrar cliente");
            System.out.println("2. Buscar por ID");
            System.out.println("3. Buscar por documento");
            System.out.println("4. Listar clientes");
            System.out.println("5. Actualizar contacto");
            System.out.println("0. Volver");
            opcion = leerEntero("Opción: ");
            ejecutar(opcion);
        } while (opcion != 0);
    }

    private void ejecutar(int opcion) {
        try {
            switch (opcion) {
                case 1 -> registrar();
                case 2 -> buscarPorId();
                case 3 -> buscarPorDocumento();
                case 4 -> listar();
                case 5 -> actualizarContacto();
                case 0 -> System.out.println("Volviendo...");
                default -> System.out.println("Opción no válida.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Dato no válido: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error del sistema: " + e.getMessage());
        }
    }

    private void registrar() throws Exception {
        String nombre = leerTexto("Nombre: ");
        String documento = leerTexto("Documento: ");
        String telefono = leerTexto("Teléfono (opcional): ");
        String email = leerTexto("Email: ");
        Cliente c = controlador.registrar(nombre, documento, telefono, email);
        System.out.println("Registrado: " + c);
    }

    private void buscarPorId() throws Exception {
        int id = leerEntero("ID: ");
        controlador.buscarPorId(id)
                .ifPresentOrElse(c -> System.out.println(c),
                        () -> System.out.println("No existe un cliente con ese ID."));
    }

    private void buscarPorDocumento() throws Exception {
        String doc = leerTexto("Documento: ");
        controlador.buscarPorDocumento(doc)
                .ifPresentOrElse(c -> System.out.println(c),
                        () -> System.out.println("No existe un cliente con ese documento."));
    }

    private void listar() throws Exception {
        List<Cliente> lista = controlador.listar();
        if (lista.isEmpty()) {
            System.out.println("No hay clientes registrados.");
        } else {
            lista.forEach(System.out::println);
        }
    }

    private void actualizarContacto() throws Exception {
        int id = leerEntero("ID del cliente: ");
        String telefono = leerTexto("Nuevo teléfono: ");
        String email = leerTexto("Nuevo email: ");
        boolean ok = controlador.actualizarContacto(id, telefono, email);
        System.out.println(ok ? "Contacto actualizado." : "No existe un cliente con ese ID.");
    }

    private String leerTexto(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine();
    }

    private int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Ingrese un número válido.");
            }
        }
    }
}