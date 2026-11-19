package com.carmotors.modelo;

import java.math.BigDecimal;
import java.util.List;

/** Datos de un servicio necesarios para facturar (lectura; no se persiste). */
public record ServicioFacturable(int servicioId, String estado, BigDecimal costoManoObra,
                                 String clienteNombre, String clienteDocumento,
                                 String vehiculoDescripcion, List<DetalleFactura> detalles) {

    public ServicioFacturable {
        detalles = detalles == null ? List.of() : List.copyOf(detalles);
    }
}