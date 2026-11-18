package com.carmotors.modelo;

import java.time.LocalDateTime;

public record ServicioTecnico(int servicioId, int tecnicoId, LocalDateTime fechaAsignacion) {
}
