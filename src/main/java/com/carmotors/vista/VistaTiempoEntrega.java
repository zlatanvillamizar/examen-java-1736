package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;

import java.io.PrintStream;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.Scanner;

final class VistaTiempoEntrega {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final Scanner entrada;
    private final PrintStream salida;
    private final ServicioControlador controlador;

    VistaTiempoEntrega(Scanner entrada, PrintStream salida, ServicioControlador controlador) {
        this.entrada = entrada;
        this.salida = salida;
        this.controlador = controlador;
    }

    void mostrar() {
        var reportes = new VistaReportes();
        while (true) {
            salida.println("""
                    === Tiempo Estimado de Entrega ===
                    1. Consultar estimación por ID de orden
                    2. Ver Tiempos Promedio de Reparación
                    0. Volver al menú principal""");
            salida.print("Opción: ");
            if (!entrada.hasNextLine()) {
                return;
            }
            try {
                switch (entrada.nextLine().strip()) {
                    case "0" -> { return; }
                    case "1" -> consultarOrden();
                    case "2" -> reportes.mostrarTiemposPromedioReparacion(
                            salida, controlador.consultarTiemposPromedioReparacion());
                    default -> salida.println("Opción inválida. Elija un número entre 0 y 2.");
                }
            } catch (IllegalArgumentException e) {
                salida.println("Ingrese un ID de orden entero positivo.");
            } catch (NoSuchElementException e) {
                salida.println(e.getMessage());
            } catch (SQLException e) {
                salida.println(e.getErrorCode() == 1146
                        ? "Falta una tabla requerida. Revise el esquema y la migración 002_tiempo_entrega.sql."
                        : "Error de base de datos. Revise la configuración de MySQL.");
            } catch (IllegalStateException e) {
                salida.println("Error de base de datos. Revise la configuración de MySQL.");
            }
        }
    }

    private void consultarOrden() throws SQLException {
        salida.print("ID de la orden: ");
        if (!entrada.hasNextLine()) {
            return;
        }
        int id = Integer.parseInt(entrada.nextLine().strip());
        controlador.consultarTiempoEntrega(id).ifPresentOrElse(estimacion -> {
            var tiempo = estimacion.tiempo();
            if (estimacion.provisional()) {
                salida.println("Estimación provisional basada en la fecha de entrega registrada. Aún no hay historial para este tipo.");
            }
            salida.printf("Tiempo estimado de espera: %s%n",
                    VistaReportes.formatearDuracion(tiempo.duracionEstimada()));
            salida.printf("Fecha estimada de entrega: %s%n", FECHA.format(tiempo.fechaEntregaEstimada()));
        }, () -> salida.println("No hay órdenes finalizadas con ambas fechas para estimar este tipo de mantenimiento. "
                + "La orden tampoco tiene fecha de entrega registrada."));
    }
}
