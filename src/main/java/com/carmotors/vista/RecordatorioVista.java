package com.carmotors.vista;

import com.carmotors.controlador.RecordatorioControlador;
import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Recordatorio;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class RecordatorioVista {

    private final RecordatorioControlador controlador;
    private final Scanner scanner;

    private static final class EntradaEofException extends RuntimeException {
    }

    public RecordatorioVista() {
        this(new RecordatorioControlador(), new Scanner(System.in));
    }

    public RecordatorioVista(RecordatorioControlador controlador, Scanner scanner) {
        this.controlador = controlador;
        this.scanner = scanner;
    }

    public void mostrarMenu() {
        int opcion;
        do {
            System.out.println("\n=== RECORDATORIOS DE MANTENIMIENTO ===");
            System.out.println("1. Crear recordatorio");
            System.out.println("2. Listar pendientes");
            System.out.println("3. Listar próximos a vencer");
            System.out.println("4. Listar por vehículo");
            System.out.println("5. Marcar como notificado");
            System.out.println("6. Ver alertas vencidas");
            System.out.println("7. Ver alertas próximas");
            System.out.println("8. Ver alertas de un vehículo");
            System.out.println("9. Notificar recordatorio (evita duplicados)");
            System.out.println("0. Volver");
            try {
                opcion = leerEntero("Opción: ");
                ejecutar(opcion);
            } catch (EntradaEofException e) {
                System.out.println("\nEntrada finalizada (EOF). Volviendo...");
                return;
            }
        } while (opcion != 0);
    }

    private void ejecutar(int opcion) {
        try {
            switch (opcion) {
                case 1 -> crear();
                case 2 -> mostrar(controlador.listarPendientes());
                case 3 -> mostrar(controlador.listarProximos(leerEntero("Días de anticipación: ")));
                case 4 -> mostrar(controlador.listarPorVehiculo(leerEntero("ID del vehículo: ")));
                case 5 -> marcarNotificado();
                case 6 -> mostrarAlertas(controlador.listarAlertasVencidas());
                case 7 -> mostrarAlertas(controlador.listarAlertasProximas(
                        leerEntero("Días de anticipación: ")));
                case 8 -> {
                    int vehiculoId = leerEntero("ID del vehículo: ");
                    int dias = leerEntero("Días de anticipación: ");
                    mostrarAlertas(controlador.listarAlertasPorVehiculo(vehiculoId, dias));
                }
                case 9 -> notificar();
                case 0 -> System.out.println("Volviendo...");
                default -> System.out.println("Opción no válida.");
            }
        } catch (EntradaEofException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Dato no válido: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error del sistema: " + e.getMessage());
        }
    }

    private void crear() throws Exception {
        int vehiculoId = leerEntero("ID del vehículo: ");
        String servicioTxt = leerTexto("ID del servicio de origen (vacío si no aplica): ").trim();
        Integer servicioId = servicioTxt.isEmpty() ? null : Integer.valueOf(servicioTxt);
        LocalDate fecha = leerFecha("Fecha del próximo mantenimiento (AAAA-MM-DD): ");
        String descripcion = leerTexto("Descripción: ");
        Recordatorio r = controlador.crear(vehiculoId, servicioId, fecha, descripcion);
        System.out.println("Creado: " + r);
    }

    private void marcarNotificado() throws Exception {
        int id = leerEntero("ID del recordatorio: ");
        boolean ok = controlador.marcarNotificado(id);
        System.out.println(ok ? "Marcado como notificado." : "El recordatorio no existe o ya fue notificado.");
    }

    private void notificar() throws Exception {
        int id = leerEntero("ID del recordatorio a notificar: ");
        boolean ok = controlador.notificar(id);
        System.out.println(ok ? "Marcado como notificado." : "No se pudo notificar.");
    }

    private void mostrar(List<Recordatorio> lista) {
        if (lista.isEmpty()) {
            System.out.println("No hay recordatorios.");
        } else {
            lista.forEach(System.out::println);
        }
    }

    private void mostrarAlertas(List<Alerta> alertas) {
        if (alertas.isEmpty()) {
            System.out.println("No hay alertas.");
        } else {
            alertas.forEach(System.out::println);
        }
    }

    private String leerTexto(String mensaje) {
        System.out.print(mensaje);
        if (!scanner.hasNextLine()) {
            throw new EntradaEofException();
        }
        return scanner.nextLine();
    }

    private int leerEntero(String mensaje) {
        while (true) {
            try {
                return Integer.parseInt(leerTexto(mensaje).trim());
            } catch (NumberFormatException e) {
                System.out.println("Ingrese un número válido.");
            }
        }
    }

    private LocalDate leerFecha(String mensaje) {
        while (true) {
            try {
                return LocalDate.parse(leerTexto(mensaje).trim());
            } catch (DateTimeParseException e) {
                System.out.println("Formato inválido. Use AAAA-MM-DD.");
            }
        }
    }
}
