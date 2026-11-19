package com.carmotors.vista;

import com.carmotors.controlador.FacturaControlador;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

public class FacturaVista {

    private final FacturaControlador controlador;
    private final Scanner scanner;

    private static final class EntradaEofException extends RuntimeException {
    }

    public FacturaVista() {
        this(new FacturaControlador(), new Scanner(System.in));
    }

    public FacturaVista(FacturaControlador controlador, Scanner scanner) {
        this.controlador = Objects.requireNonNull(controlador, "El controlador es obligatorio");
        this.scanner = Objects.requireNonNull(scanner, "El scanner es obligatorio");
    }

    public void mostrarMenu() {
        int opcion;
        do {
            System.out.println("\n=== FACTURACIÓN ===");
            System.out.println("1. Generar factura de un servicio");
            System.out.println("2. Consultar factura por ID");
            System.out.println("3. Listar facturas");
            System.out.println("0. Volver");
            try {
                opcion = leerEntero("Opción: ");
                ejecutar(opcion);
            } catch (EntradaEofException e) {
                System.out.println("\nEntrada finalizada (EOF). Volviendo...");
                break;
            }
        } while (opcion != 0);
    }

    private void ejecutar(int opcion) {
        try {
            switch (opcion) {
                case 1 -> imprimir(controlador.crear(leerEntero("ID del servicio: ")));
                case 2 -> controlador.buscarPorId(leerEntero("ID de la factura: "))
                        .ifPresentOrElse(this::imprimir,
                                () -> System.out.println("No existe esa factura."));
                case 3 -> listar(controlador.listar());
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

    private void listar(List<Factura> lista) {
        if (lista.isEmpty()) {
            System.out.println("No hay facturas.");
        } else {
            lista.forEach(System.out::println);
        }
    }

    /** Factura en texto plano (requisito del enunciado). */
    private void imprimir(Factura f) {
        System.out.println("\n=========== FACTURA " + f.getId() + " ===========");
        System.out.println("Fecha:     " + f.getFechaEmision());
        System.out.println("Servicio:  " + f.getServicioId());
        if (f.getClienteNombre() != null) {
            System.out.println("Cliente:   " + f.getClienteNombre()
                    + " (" + f.getClienteDocumento() + ")");
            System.out.println("Vehículo:  " + f.getVehiculoDescripcion());
        }
        System.out.println("--------------------------------------");
        if (!f.isDetalleHistorico()) {
            System.out.println("Detalle histórico no disponible para esta factura.");
        }
        for (DetalleFactura d : f.getDetalles()) {
            System.out.printf("%-22s %3d x %10s = %10s%n", d.nombreRepuesto(), d.cantidad(),
                    d.precioUnitario().toPlainString(), d.subtotal().toPlainString());
        }
        System.out.println("--------------------------------------");
        System.out.println("Repuestos:    " + f.getTotalRepuestos().toPlainString());
        System.out.println("Mano de obra: " + f.getTotalManoObra().toPlainString());
        System.out.println("Impuestos:    " + f.getImpuestos().toPlainString());
        System.out.println("TOTAL:        " + f.getTotalFinal().toPlainString());
        System.out.println("======================================");
    }

    private int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            if (!scanner.hasNextLine()) {
                throw new EntradaEofException();
            }
            try {
                String linea = scanner.nextLine().trim();
                return Integer.parseInt(linea);
            } catch (NoSuchElementException e) {
                throw new EntradaEofException();
            } catch (NumberFormatException e) {
                System.out.println("Ingrese un número válido.");
            }
        }
    }
}
