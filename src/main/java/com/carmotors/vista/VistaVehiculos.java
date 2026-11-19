package com.carmotors.vista;

import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.modelo.Vehiculo;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/** Vista de consola: lectura, presentación y delegación al controlador de Vehículos. */
final class VistaVehiculos {
    private final Scanner entrada;
    private final PrintStream salida;
    private final VehiculoControlador controlador;

    VistaVehiculos(Scanner entrada, PrintStream salida, VehiculoControlador controlador) {
        this.entrada = Objects.requireNonNull(entrada);
        this.salida = Objects.requireNonNull(salida);
        this.controlador = Objects.requireNonNull(controlador);
    }

    void mostrar() {
        while (true) {
            salida.println("""
                    === Vehículos ===
                    1. Registrar vehículo
                    2. Buscar vehículo por ID
                    3. Buscar vehículo por placa
                    4. Listar vehículos
                    0. Volver al menú principal""");
            try {
                String opcion = leer("Opción");
                if ("0".equals(opcion)) {
                    return;
                }
                switch (opcion) {
                    case "1" -> ejecutar(this::registrar);
                    case "2" -> ejecutar(this::buscarPorId);
                    case "3" -> ejecutar(this::buscarPorPlaca);
                    case "4" -> ejecutar(this::listar);
                    default -> salida.println("Opción inválida. Elija un número entre 0 y 4.");
                }
            } catch (FinEntrada e) {
                return;
            }
        }
    }

    private void registrar() throws SQLException {
        int clienteId = enteroPositivo("ID del cliente");
        String placa = obligatorio("Placa");
        String marca = obligatorio("Marca");
        String modelo = obligatorio("Modelo");
        int anio = enteroPositivo("Año");
        Vehiculo vehiculo = controlador.registrarVehiculo(new Vehiculo(clienteId, placa, marca, modelo, anio));
        salida.println("Vehículo registrado con ID " + vehiculo.getId() + ".");
    }

    private void buscarPorId() throws SQLException {
        int id = enteroPositivo("ID del vehículo");
        controlador.buscarVehiculo(id).ifPresentOrElse(this::mostrarVehiculo,
                () -> salida.println("No se encontró el vehículo " + id + "."));
    }

    private void buscarPorPlaca() throws SQLException {
        String placa = obligatorio("Placa");
        controlador.buscarPorPlaca(placa).ifPresentOrElse(this::mostrarVehiculo,
                () -> salida.println("No se encontró un vehículo con esa placa."));
    }

    private void listar() throws SQLException {
        List<Vehiculo> vehiculos = controlador.listarVehiculos();
        if (vehiculos.isEmpty()) {
            salida.println("No hay vehículos registrados.");
        }
        vehiculos.forEach(this::mostrarVehiculo);
    }

    private void mostrarVehiculo(Vehiculo vehiculo) {
        salida.printf("Vehículo %d | Cliente %d | Placa %s | %s %s | Año %d%n",
                vehiculo.getId(), vehiculo.getClienteId(), vehiculo.getPlaca(),
                vehiculo.getMarca(), vehiculo.getModelo(), vehiculo.getAnio());
    }

    private String obligatorio(String etiqueta) {
        String valor = leer(etiqueta);
        if (valor.isBlank()) {
            throw new IllegalArgumentException(etiqueta + " es obligatorio.");
        }
        return valor;
    }

    private int enteroPositivo(String etiqueta) {
        String valor = leer(etiqueta);
        final int numero;
        try {
            numero = Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(etiqueta + " debe ser un número entero.");
        }
        if (numero <= 0) {
            throw new IllegalArgumentException(etiqueta + " debe ser positivo.");
        }
        return numero;
    }

    private String leer(String etiqueta) {
        salida.print(etiqueta + ": ");
        if (!entrada.hasNextLine()) {
            throw new FinEntrada();
        }
        return entrada.nextLine().strip();
    }

    private void ejecutar(Operacion operacion) {
        try {
            operacion.ejecutar();
        } catch (IllegalArgumentException e) {
            salida.println("Validación: " + e.getMessage());
        } catch (SQLException e) {
            salida.println("Error de base de datos. Revise los datos ingresados y la conexión.");
        } catch (IllegalStateException e) {
            salida.println("Error: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operacion {
        void ejecutar() throws SQLException;
    }

    private static final class FinEntrada extends RuntimeException {
    }
}
