package com.carmotors.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AlertaTest {

    private final LocalDate hoy = LocalDate.of(2026, 11, 18);

    private Recordatorio con(LocalDate fecha) {
        return new Recordatorio(1, null, fecha, "x");
    }

    @Test
    void fechaPasada_esVencida() {
        Alerta a = Alerta.desde(con(hoy.minusDays(3)), hoy);
        assertEquals(Alerta.Tipo.VENCIDA, a.getTipo());
        assertEquals(-3, a.getDiasRestantes());
    }

    @Test
    void fechaHoy_esProximaConCeroDias() {
        Alerta a = Alerta.desde(con(hoy), hoy);
        assertEquals(Alerta.Tipo.PROXIMA, a.getTipo());
        assertEquals(0, a.getDiasRestantes());
    }

    @Test
    void fechaFutura_esProxima() {
        Alerta a = Alerta.desde(con(hoy.plusDays(5)), hoy);
        assertEquals(Alerta.Tipo.PROXIMA, a.getTipo());
        assertEquals(5, a.getDiasRestantes());
    }

    @Test
    void argumentosNulos_lanzanExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> Alerta.desde(null, hoy));
        assertThrows(IllegalArgumentException.class, () -> Alerta.desde(con(hoy), null));
    }
}