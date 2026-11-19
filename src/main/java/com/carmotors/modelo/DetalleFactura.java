package com.carmotors.modelo;

import java.math.BigDecimal;

/** Línea de repuesto de una factura. Conserva la cantidad y el precio al emitir. */
public record DetalleFactura(int repuestoId, String nombreRepuesto,
                             int cantidad, BigDecimal precioUnitario) {

    public DetalleFactura {
        if (repuestoId <= 0) {
            throw new IllegalArgumentException("El repuesto debe tener un ID positivo");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (precioUnitario == null || precioUnitario.signum() < 0) {
            throw new IllegalArgumentException("El precio unitario no puede ser negativo");
        }
    }

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}