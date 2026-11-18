package com.carmotors.modelo;

import java.math.BigDecimal;

/**
 * Repuesto del inventario. Refleja la tabla {@code repuestos} de
 * {@code database/bloques/inventario.sql}.
 *
 * <p>El {@code id} es {@code null} mientras el repuesto no se ha guardado
 * (lo asigna {@code AUTO_INCREMENT}).</p>
 */
public record Repuesto(
        Integer id,
        String nombre,
        String marca,
        int repuestoTipoId,
        BigDecimal precioVenta,
        int cantidadStock,
        int stockMinimo) {

    public Repuesto {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("El id del repuesto debe ser positivo");
        }
        nombre = textoObligatorio(nombre, "nombre", 255);
        marca = textoObligatorio(marca, "marca", 100);
        if (repuestoTipoId <= 0) {
            throw new IllegalArgumentException("El tipo de repuesto debe ser un id positivo");
        }
        if (precioVenta == null) {
            throw new IllegalArgumentException("El precio de venta es obligatorio");
        }
        if (precioVenta.signum() < 0) {
            throw new IllegalArgumentException("El precio de venta no puede ser negativo");
        }
        if (precioVenta.scale() > 2 || precioVenta.precision() - precioVenta.scale() > 8) {
            throw new IllegalArgumentException("El precio de venta debe caber en DECIMAL(10,2)");
        }
        if (cantidadStock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        if (stockMinimo < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo");
        }
    }

    /** Crea un repuesto nuevo, todavía sin id. */
    public static Repuesto nuevo(String nombre, String marca, int repuestoTipoId,
                                 BigDecimal precioVenta, int cantidadStock, int stockMinimo) {
        return new Repuesto(null, nombre, marca, repuestoTipoId, precioVenta, cantidadStock, stockMinimo);
    }

    /** Devuelve una copia con el id asignado por la base de datos. */
    public Repuesto conId(int nuevoId) {
        return new Repuesto(nuevoId, nombre, marca, repuestoTipoId, precioVenta, cantidadStock, stockMinimo);
    }

    /** {@code true} cuando el stock está en el mínimo o por debajo. */
    public boolean estaEnStockMinimo() {
        return cantidadStock <= stockMinimo;
    }

    private static String textoObligatorio(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        }
        String limpio = valor.strip();
        if (limpio.length() > longitudMaxima) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " no puede superar " + longitudMaxima + " caracteres");
        }
        return limpio;
    }
}
