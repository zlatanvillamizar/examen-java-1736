package com.carmotors.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Alerta derivada de un Recordatorio. No se persiste: no tiene tabla. */
public class Alerta {

    public enum Tipo { VENCIDA, PROXIMA }

    private final Recordatorio recordatorio;
    private final Tipo tipo;
    private final long diasRestantes; // negativo si ya venció

    private Alerta(Recordatorio recordatorio, Tipo tipo, long diasRestantes) {
        this.recordatorio = recordatorio;
        this.tipo = tipo;
        this.diasRestantes = diasRestantes;
    }

    public static Alerta desde(Recordatorio r, LocalDate hoy) {
        if (r == null || hoy == null) {
            throw new IllegalArgumentException("Recordatorio y fecha son obligatorios");
        }
        long dias = ChronoUnit.DAYS.between(hoy, r.getFechaProximoMantenimiento());
        Tipo tipo = dias < 0 ? Tipo.VENCIDA : Tipo.PROXIMA;
        return new Alerta(r, tipo, dias);
    }

    public Recordatorio getRecordatorio() { return recordatorio; }
    public Tipo getTipo() { return tipo; }
    public long getDiasRestantes() { return diasRestantes; }

    @Override
    public String toString() {
        String cuando = tipo == Tipo.VENCIDA
                ? "venció hace " + Math.abs(diasRestantes) + " día(s)"
                : "vence en " + diasRestantes + " día(s)";
        return "[" + tipo + "] " + cuando + " -> " + recordatorio;
    }
}