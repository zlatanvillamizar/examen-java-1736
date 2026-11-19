package com.carmotors.vista;

import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.StockInsuficienteException;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Lectura validada y manejo de errores compartidos por {@link VistaInventario} y
 * {@link VistaProveedores}.
 *
 * <p>Usa el {@link Scanner} que recibe (el del menú principal) y <b>nunca lo cierra</b>,
 * para no cerrar {@code System.in}. Si la entrada se acaba, {@link #leer} lanza
 * {@link FinEntrada} y la vista vuelve al menú que la llamó.</p>
 */
final class EntradaConsola {

    /** La entrada se acabó (por ejemplo, fin de archivo o Ctrl+D). */
    static final class FinEntrada extends RuntimeException {
        FinEntrada() {
            super(null, null, false, false);
        }
    }

    /** Acción de una opción del menú. */
    @FunctionalInterface
    interface Operacion {
        void ejecutar() throws SQLException;
    }

    private final Scanner entrada;
    private final PrintStream salida;

    EntradaConsola(Scanner entrada, PrintStream salida) {
        this.entrada = Objects.requireNonNull(entrada, "La entrada es obligatoria");
        this.salida = Objects.requireNonNull(salida, "La salida es obligatoria");
    }

    String leer(String etiqueta) {
        salida.print(etiqueta + ": ");
        if (!entrada.hasNextLine()) {
            throw new FinEntrada();
        }
        return entrada.nextLine().strip();
    }

    String obligatorio(String etiqueta) {
        String valor = leer(etiqueta);
        if (valor.isEmpty()) {
            throw new IllegalArgumentException(etiqueta + " es obligatorio.");
        }
        return valor;
    }

    /** Devuelve {@code null} si se deja vacío. */
    String opcional(String etiqueta) {
        String valor = leer(etiqueta + " (Enter para omitir)");
        return valor.isEmpty() ? null : valor;
    }

    /**
     * Para actualizar un dato opcional: Enter conserva {@code actual}, un guion lo borra
     * y cualquier otro texto lo reemplaza.
     */
    String actualizado(String etiqueta, String actual) {
        String valor = leer(etiqueta + " [" + (actual == null ? "sin registrar" : actual)
                + "] (Enter conserva, - borra)");
        if (valor.isEmpty()) {
            return actual;
        }
        return "-".equals(valor) ? null : valor;
    }

    int enteroPositivo(String etiqueta) {
        int valor = entero(etiqueta);
        if (valor <= 0) {
            throw new IllegalArgumentException(etiqueta + " debe ser positivo.");
        }
        return valor;
    }

    int enteroNoNegativo(String etiqueta) {
        int valor = entero(etiqueta);
        if (valor < 0) {
            throw new IllegalArgumentException(etiqueta + " no puede ser negativo.");
        }
        return valor;
    }

    BigDecimal decimalNoNegativo(String etiqueta) {
        String valor = obligatorio(etiqueta + " (punto decimal, por ejemplo 25000.50)");
        BigDecimal numero;
        try {
            numero = new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(etiqueta + " debe ser un número con punto decimal, por ejemplo 25000.50.");
        }
        if (numero.signum() < 0) {
            throw new IllegalArgumentException(etiqueta + " no puede ser negativo.");
        }
        return numero;
    }

    private int entero(String etiqueta) {
        String valor = leer(etiqueta);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(etiqueta + " debe ser un número entero.");
        }
    }

    /**
     * Ejecuta una opción y muestra los errores esperados sin cortar el menú. Los detalles
     * internos de JDBC no se muestran al usuario.
     *
     * @param mensajeIntegridad texto para cuando la base rechaza datos por una clave
     *                          foránea o un valor único
     */
    void ejecutar(Operacion operacion, String mensajeIntegridad) {
        try {
            operacion.ejecutar();
        } catch (StockInsuficienteException e) {
            salida.printf("Stock insuficiente: se pidieron %d y hay %d disponibles. No se registró el movimiento.%n",
                    e.getSolicitado(), e.getDisponible());
        } catch (RepuestoNoEncontradoException e) {
            salida.println("No existe el repuesto con ID " + e.getRepuestoId() + ".");
        } catch (IllegalArgumentException | NoSuchElementException e) {
            salida.println("Validación: " + e.getMessage());
        } catch (IllegalStateException e) {
            salida.println("Error: " + e.getMessage());
        } catch (SQLIntegrityConstraintViolationException e) {
            salida.println(mensajeIntegridad);
        } catch (SQLException e) {
            salida.println("Error de base de datos. Revise la conexión y vuelva a intentarlo.");
        }
    }
}
