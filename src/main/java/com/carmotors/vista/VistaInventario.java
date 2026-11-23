package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.modelo.Repuesto;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Vista de consola del inventario de repuestos: registro, consultas y movimientos de
 * stock. Solo lee, muestra y delega en {@link InventarioControlador}; las reglas (stock
 * nunca negativo, cantidades positivas, datos del repuesto) están en el servicio y el
 * modelo.
 *
 * <p>Recibe el {@link Scanner} compartido del menú principal y no lo cierra.</p>
 */
final class VistaInventario {

    private static final String MENSAJE_INTEGRIDAD =
            "La base de datos rechazó el repuesto: verifique que el tipo de repuesto exista.";

    private final EntradaConsola consola;
    private final PrintStream salida;
    private final InventarioControlador controlador;

    VistaInventario(Scanner entrada, PrintStream salida, InventarioControlador controlador) {
        this.consola = new EntradaConsola(entrada, salida);
        this.salida = Objects.requireNonNull(salida);
        this.controlador = Objects.requireNonNull(controlador, "El controlador de inventario es obligatorio");
    }

    void mostrar() {
        while (true) {
            salida.println("""
                    === Inventario ===
                    1. Registrar repuesto
                    2. Buscar repuesto por ID
                    3. Listar repuestos
                    4. Consultar stock disponible
                    5. Registrar entrada de inventario
                    6. Registrar salida de inventario
                    7. Listar repuestos en stock mínimo
                    8. Ver repuestos de una orden de servicio
                    0. Volver al menú principal""");
            try {
                String opcion = consola.leer("Opción");
                switch (opcion) {
                    case "0" -> {
                        return;
                    }
                    case "1" -> consola.ejecutar(this::registrar, MENSAJE_INTEGRIDAD);
                    case "2" -> consola.ejecutar(this::buscar, MENSAJE_INTEGRIDAD);
                    case "3" -> consola.ejecutar(this::listar, MENSAJE_INTEGRIDAD);
                    case "4" -> consola.ejecutar(this::consultarStock, MENSAJE_INTEGRIDAD);
                    case "5" -> consola.ejecutar(this::registrarEntrada, MENSAJE_INTEGRIDAD);
                    case "6" -> consola.ejecutar(this::registrarSalida, MENSAJE_INTEGRIDAD);
                    case "7" -> consola.ejecutar(this::listarStockMinimo, MENSAJE_INTEGRIDAD);
                    case "8" -> consola.ejecutar(this::repuestosDeOrden, MENSAJE_INTEGRIDAD);
                    default -> salida.println("Opción inválida. Elija un número entre 0 y 8.");
                }
            } catch (EntradaConsola.FinEntrada e) {
                return;
            }
        }
    }

    private void registrar() throws SQLException {
        String nombre = consola.obligatorio("Nombre");
        String marca = consola.obligatorio("Marca");
        Map<Integer, String> tipos = controlador.listarTiposDeRepuesto();
        if (tipos.isEmpty()) {
            salida.println("No hay tipos de repuesto registrados. Cárguelos con "
                    + "docs/inventario/repuesto_tipos_iniciales.sql antes de registrar repuestos.");
            return;
        }
        salida.println("Tipos disponibles: " + tipos.entrySet().stream()
                .map(tipo -> tipo.getKey() + " = " + tipo.getValue())
                .collect(Collectors.joining(", ")));
        int tipo = consola.enteroPositivo("ID del tipo de repuesto");
        if (!tipos.containsKey(tipo)) {
            salida.println("El tipo de repuesto " + tipo + " no existe. Elija uno de la lista.");
            return;
        }
        BigDecimal precio = consola.decimalNoNegativo("Precio de venta");
        int stock = consola.enteroNoNegativo("Stock inicial");
        int minimo = consola.enteroNoNegativo("Stock mínimo");
        Repuesto repuesto = controlador.registrarRepuesto(nombre, marca, tipo, precio, stock, minimo);
        salida.println("Repuesto registrado con ID " + repuesto.id() + ".");
    }

    private void buscar() throws SQLException {
        int id = consola.enteroPositivo("ID del repuesto");
        controlador.buscarRepuesto(id).ifPresentOrElse(this::mostrarRepuesto,
                () -> salida.println("No se encontró el repuesto " + id + "."));
    }

    private void listar() throws SQLException {
        mostrarRepuestos(controlador.listarRepuestos(), "No hay repuestos registrados.");
    }

    private void listarStockMinimo() throws SQLException {
        mostrarRepuestos(controlador.listarRepuestosEnStockMinimo(), "Ningún repuesto está en su stock mínimo.");
    }

    private void consultarStock() throws SQLException {
        int id = consola.enteroPositivo("ID del repuesto");
        controlador.buscarRepuesto(id).ifPresentOrElse(repuesto -> {
            salida.printf("Repuesto %d | %s | Stock disponible: %d | Stock mínimo: %d%n",
                    repuesto.id(), repuesto.nombre(), repuesto.cantidadStock(), repuesto.stockMinimo());
            salida.println("(El stock disponible ya excluye las unidades reservadas para servicios.)");
            if (repuesto.estaEnStockMinimo()) {
                salida.println("Alerta: el repuesto está en su stock mínimo o por debajo; conviene reponerlo.");
            }
        }, () -> salida.println("No se encontró el repuesto " + id + "."));
    }

    private void registrarEntrada() throws SQLException {
        int id = consola.enteroPositivo("ID del repuesto");
        int cantidad = consola.enteroPositivo("Cantidad que ingresa");
        int stock = controlador.registrarEntrada(id, cantidad);
        salida.println("Entrada registrada. Stock actual: " + stock + ".");
    }

    private void registrarSalida() throws SQLException {
        int id = consola.enteroPositivo("ID del repuesto");
        int cantidad = consola.enteroPositivo("Cantidad que sale");
        int stock = controlador.registrarSalida(id, cantidad);
        salida.println("Salida registrada. Stock actual: " + stock + ".");
    }

    private void repuestosDeOrden() throws SQLException {
        int orden = consola.enteroPositivo("ID de la orden de servicio");
        List<ReservaRepuestoDAO.Linea> lineas = controlador.listarRepuestosDeOrden(orden);
        if (lineas.isEmpty()) {
            salida.println("La orden " + orden + " no tiene repuestos reservados ni usados.");
            return;
        }
        int pendientes = 0;
        for (ReservaRepuestoDAO.Linea linea : lineas) {
            String nombre = controlador.buscarRepuesto(linea.repuestoId())
                    .map(Repuesto::nombre).orElse("(repuesto eliminado)");
            salida.printf("Repuesto %d | %s | Reservadas sin usar: %d | Usadas: %d | Precio unitario: %s%n",
                    linea.repuestoId(), nombre, linea.cantidadReservada(), linea.cantidadUsada(),
                    linea.precioUnitarioVenta().toPlainString());
            pendientes += linea.cantidadReservada();
        }
        if (pendientes > 0) {
            salida.println("Hay " + pendientes + " unidades reservadas sin usar: se devuelven al stock "
                    + "al liberar, completar o cancelar la orden.");
        }
    }

    private void mostrarRepuestos(List<Repuesto> repuestos, String mensajeSinResultados) {
        if (repuestos.isEmpty()) {
            salida.println(mensajeSinResultados);
        }
        repuestos.forEach(this::mostrarRepuesto);
    }

    private void mostrarRepuesto(Repuesto repuesto) {
        salida.printf("Repuesto %d | %s | Marca: %s | Tipo: %d | Precio: %s | Stock: %d | Mínimo: %d%s%n",
                repuesto.id(), repuesto.nombre(), repuesto.marca(), repuesto.repuestoTipoId(),
                repuesto.precioVenta().toPlainString(), repuesto.cantidadStock(), repuesto.stockMinimo(),
                repuesto.estaEnStockMinimo() ? " | ¡Stock mínimo!" : "");
    }
}
