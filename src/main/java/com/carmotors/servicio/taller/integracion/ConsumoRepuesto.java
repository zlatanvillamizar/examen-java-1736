package com.carmotors.servicio.taller.integracion;

public record ConsumoRepuesto(int servicioId, int repuestoId, int cantidad) {
    public ConsumoRepuesto {
        if (servicioId <= 0 || repuestoId <= 0) {
            throw new IllegalArgumentException("Los identificadores de servicio y repuesto deben ser positivos");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a consumir debe ser positiva");
        }
    }
}
