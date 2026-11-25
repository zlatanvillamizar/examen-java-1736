package com.carmotors.servicio.taller;

import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.ServicioDAO.ServicioConFechas;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TiemposPromedioReparacionTest {
    private static final LocalDateTime RECEPCION = LocalDateTime.of(2026, 11, 17, 22, 0);

    @Test
    void promediaCadaTipoConDuracionesDeHorasYDias() throws Exception {
        ServicioService servicio = servicioCon(List.of(
                fila("Preventivo", 4, Duration.ofHours(4).plusMinutes(1).plusSeconds(30)),
                fila("Correctivo", 8, Duration.ofDays(1).plusHours(7)),
                fila("Preventivo", 4, Duration.ofHours(5).plusMinutes(2).plusSeconds(30)),
                fila("Correctivo", 8, Duration.ofDays(3).plusHours(7).plusMinutes(30))));

        assertEquals(Map.of("Preventivo", Duration.ofHours(4).plusMinutes(32),
                "Correctivo", Duration.ofDays(2).plusHours(7).plusMinutes(15)),
                servicio.calcularTiemposPromedioReparacion());
    }

    @Test
    void omiteTiposSinServiciosYConservaDuracionesDeCero() throws Exception {
        assertEquals(Map.of("Preventivo", Duration.ZERO),
                servicioCon(List.of(fila("Preventivo", 4, Duration.ZERO)))
                        .calcularTiemposPromedioReparacion());
        assertEquals(Map.of(), servicioCon(List.of()).calcularTiemposPromedioReparacion());
    }

    private static ServicioService servicioCon(List<ServicioConFechas> filas) {
        return new ServicioService(new ServicioDAO() {
            @Override
            public List<ServicioConFechas> listarCompletadosConFechas() {
                return filas;
            }
        }, new TecnicoDAO());
    }

    private static ServicioConFechas fila(String tipo, int tipoId, Duration duracion) {
        return new ServicioConFechas(new Servicio(1, 1, tipoId, RECEPCION, RECEPCION.plus(duracion),
                "Revisión", null, 3), tipo);
    }
}
