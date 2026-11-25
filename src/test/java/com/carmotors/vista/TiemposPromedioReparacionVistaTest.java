package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.servicio.taller.ServicioService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TiemposPromedioReparacionVistaTest {
    @Test
    void muestraElReporteDesdeElMenuConUnTipoSinServicios() {
        ServicioDAO dao = new ServicioDAO() {
            @Override
            public List<ServicioConFechas> listarCompletadosConFechas() {
                LocalDateTime recepcion = LocalDateTime.of(2026, 11, 17, 22, 0);
                Servicio orden = new Servicio(1, 1, 2, recepcion,
                        recepcion.plusDays(2).plusHours(7).plusMinutes(15), "Revisión", null, 3);
                return List.of(new ServicioConFechas(orden, "Correctivo"));
            }
        };
        var controlador = new ServicioControlador(new ServicioService(dao, new TecnicoDAO()));
        var captura = new ByteArrayOutputStream();
        try (var entrada = new Scanner("12\n0\n");
             var salida = new PrintStream(captura, true, StandardCharsets.UTF_8)) {
            new VistaServicios(entrada, salida, controlador).mostrar();
        }
        String consola = captura.toString(StandardCharsets.UTF_8);
        assertTrue(consola.contains("12. Ver Tiempos Promedio de Reparación"));
        assertTrue(consola.contains("INFORME DE TIEMPOS PROMEDIO DE REPARACIÓN"));
        assertTrue(consola.contains("Preventivo            | Sin servicios completados con ambas fechas"));
        assertTrue(consola.contains("Correctivo            | 2 Días, 7 Horas, 15 Minutos"));
    }
}
