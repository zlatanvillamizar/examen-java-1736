package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.Tecnico;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.NoSuchElementException;
import java.util.Scanner;

final class VistaServicios {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private final Scanner entrada;
    private final PrintStream salida;
    private final ServicioControlador controlador;

    VistaServicios(Scanner entrada, PrintStream salida, ServicioControlador controlador) {
        this.entrada = entrada;
        this.salida = salida;
        this.controlador = controlador;
    }

    void mostrar() {
        while (true) {
            salida.println("""
                    === Servicios ===
                    1. Registrar orden
                    2. Buscar orden por ID
                    3. Listar órdenes
                    4. Actualizar estado
                    5. Actualizar fecha de entrega
                    6. Registrar técnico
                    7. Asignar técnico
                    0. Volver al menú principal""");
            try {
                switch (leer("Opción")) {
                    case "0" -> { return; }
                    case "1" -> registrarOrden();
                    case "2" -> buscarOrden();
                    case "3" -> listarOrdenes();
                    case "4" -> actualizarEstado();
                    case "5" -> actualizarEntrega();
                    case "6" -> registrarTecnico();
                    case "7" -> asignarTecnico();
                    default -> salida.println("Opción inválida. Elija un número entre 0 y 7.");
                }
            } catch (FinEntrada e) {
                return;
            } catch (IllegalArgumentException | NoSuchElementException e) {
                salida.println("Validación: " + e.getMessage());
            } catch (SQLException e) {
                salida.println("Error de base de datos. Revise los datos ingresados y la conexión.");
            } catch (IllegalStateException e) {
                salida.println("Error: " + e.getMessage());
            }
        }
    }

    private void registrarOrden() throws SQLException {
        int vehiculo = entero("ID del vehículo");
        int tipo = entero("ID del tipo de mantenimiento");
        LocalDateTime recepcion = fecha("Fecha de recepción", false);
        LocalDateTime entrega = fecha("Fecha de entrega", true);
        String descripcion = leer("Descripción del problema");
        BigDecimal costo = costoOpcional();
        int estado = entero("ID del estado");
        Servicio orden = new Servicio(null, vehiculo, tipo, recepcion, entrega, descripcion, costo, estado);
        salida.println("Orden registrada con ID " + controlador.registrarServicio(orden).valor() + ".");
    }

    private void buscarOrden() {
        int id = entero("ID de la orden");
        controlador.buscarServicio(id).ifPresentOrElse(this::mostrarOrden,
                () -> salida.println("No se encontró la orden " + id + "."));
    }

    private void listarOrdenes() {
        var ordenes = controlador.listarServicios();
        if (ordenes.isEmpty()) {
            salida.println("No hay órdenes registradas.");
        }
        ordenes.forEach(this::mostrarOrden);
    }

    private void actualizarEstado() throws SQLException {
        controlador.actualizarEstado(entero("ID de la orden"), entero("ID del nuevo estado"));
        salida.println("Estado actualizado.");
    }

    private void actualizarEntrega() throws SQLException {
        controlador.actualizarFechaEntrega(entero("ID de la orden"), fecha("Fecha de entrega", true));
        salida.println("Fecha de entrega actualizada.");
    }

    private void registrarTecnico() throws SQLException {
        String nombre = leer("Nombre");
        String especialidad = leer("Especialidad (Enter para omitir)");
        boolean activo = switch (leer("¿Activo? (s/n)")) {
            case "s", "S" -> true;
            case "n", "N" -> false;
            default -> throw new IllegalArgumentException("La respuesta debe ser s o n.");
        };
        Tecnico tecnico = new Tecnico(null, nombre, especialidad.isEmpty() ? null : especialidad, activo);
        salida.println("Técnico registrado con ID " + controlador.registrarTecnico(tecnico) + ".");
    }

    private void asignarTecnico() throws SQLException {
        int orden = entero("ID de la orden");
        int tecnico = entero("ID del técnico");
        controlador.asignarTecnico(new ServicioTecnico(orden, tecnico, fecha("Fecha de asignación", false)));
        salida.println("Técnico asignado a la orden.");
    }

    private void mostrarOrden(Servicio orden) {
        salida.printf("Orden %d | Vehículo %d | Mantenimiento %d | Estado %d%n",
                orden.id(), orden.vehiculoId(), orden.mantenimientoTipoId(), orden.estadoId());
        salida.printf("Recepción: %s | Entrega: %s | Costo de mano de obra: %s%n",
                orden.fechaRecepcion().format(FECHA),
                orden.fechaEntrega() == null ? "sin fecha" : orden.fechaEntrega().format(FECHA),
                orden.costoManoObra() == null ? "sin registrar" : orden.costoManoObra().toPlainString());
        salida.println("Descripción: " + orden.descripcionProblema());
    }

    private BigDecimal costoOpcional() {
        String valor = leer("Costo de mano de obra (punto decimal, Enter para omitir)");
        try {
            return valor.isEmpty() ? null : new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingrese un costo numérico, por ejemplo 12500.50.");
        }
    }

    private int entero(String etiqueta) {
        try {
            return Integer.parseInt(leer(etiqueta));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(etiqueta + " debe ser un número entero.");
        }
    }

    private LocalDateTime fecha(String etiqueta, boolean opcional) {
        String indicacion = opcional ? " (AAAA-MM-DD HH:mm, Enter para dejar sin fecha)" : " (AAAA-MM-DD HH:mm)";
        String valor = leer(etiqueta + indicacion);
        try {
            return opcional && valor.isEmpty() ? null : LocalDateTime.parse(valor, FECHA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(etiqueta + " debe ser una fecha y hora válidas en el formato indicado.");
        }
    }

    private String leer(String etiqueta) {
        salida.print(etiqueta + ": ");
        if (!entrada.hasNextLine()) {
            throw new FinEntrada();
        }
        return entrada.nextLine().strip();
    }

    private static final class FinEntrada extends RuntimeException {
    }
}
