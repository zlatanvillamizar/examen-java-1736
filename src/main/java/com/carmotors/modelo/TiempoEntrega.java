package com.carmotors.modelo;

import java.time.Duration;
import java.time.LocalDateTime;

public record TiempoEntrega(int servicioId, Duration duracionEstimada, LocalDateTime fechaEntregaEstimada) {
}
