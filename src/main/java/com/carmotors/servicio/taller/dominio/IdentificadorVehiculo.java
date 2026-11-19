package com.carmotors.servicio.taller.dominio;

/** Referencia lógica al vehículo; no define columnas ni relaciones SQL. */
public record IdentificadorVehiculo(long valor) {
    public IdentificadorVehiculo {
        if (valor <= 0) {
            throw new IllegalArgumentException("El identificador del vehículo debe ser positivo");
        }
    }
}
