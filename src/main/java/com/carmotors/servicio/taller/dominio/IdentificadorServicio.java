package com.carmotors.servicio.taller.dominio;

/**
 * Identificador lógico de una orden de servicio.
 * No impone un tipo físico de columna SQL.
 */
public record IdentificadorServicio(long valor) {
    public IdentificadorServicio {
        if (valor <= 0) {
            throw new IllegalArgumentException("El identificador del servicio debe ser positivo");
        }
    }
}
