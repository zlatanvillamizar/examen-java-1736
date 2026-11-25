package com.carmotors.vista;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class VistaReportes {
    void mostrarTiemposPromedioReparacion(PrintStream salida, Map<String, Duration> promedios) {
        salida.println("""
                =============================================================
                        INFORME DE TIEMPOS PROMEDIO DE REPARACIÓN
                =============================================================

                Tipo de Mantenimiento | Tiempo Promedio de Finalización
                -------------------------------------------------------------""");
        for (String tipo : List.of("Preventivo", "Correctivo")) {
            Duration promedio = promedios.get(tipo);
            String tiempo = promedio == null ? "Sin servicios completados con ambas fechas"
                    : formatearDuracion(promedio);
            salida.printf("%-21s | %s%n", tipo, tiempo);
        }
        salida.println("-------------------------------------------------------------");
    }

    static String formatearDuracion(Duration duracion) {
        return "%d Días, %d Horas, %d Minutos".formatted(
                duracion.toDays(), duracion.toHoursPart(), duracion.toMinutesPart());
    }
}
