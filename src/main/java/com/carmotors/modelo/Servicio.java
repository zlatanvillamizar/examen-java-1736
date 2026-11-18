package com.carmotors.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** El ID es nulo en una solicitud de alta y se genera al guardar. */
public record Servicio(Integer id, int vehiculoId, int mantenimientoTipoId,
                       LocalDateTime fechaRecepcion, LocalDateTime fechaEntrega,
                       String descripcionProblema, BigDecimal costoManoObra, int estadoId) {
}
