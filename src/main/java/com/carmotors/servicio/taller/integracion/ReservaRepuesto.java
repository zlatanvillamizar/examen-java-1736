package com.carmotors.servicio.taller.integracion;

public record ReservaRepuesto(int servicioId, int repuestoId, int cantidad) {
    public ReservaRepuesto {
        validarId(servicioId, "servicio");
        validarId(repuestoId, "repuesto");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar/liberar debe ser positiva");
        }
    }

    private static void validarId(int id, String entidad) {
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador de " + entidad + " debe ser positivo");
        }
    }
}
