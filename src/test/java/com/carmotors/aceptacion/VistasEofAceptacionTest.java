package com.carmotors.aceptacion;

import com.carmotors.controlador.FacturaControlador;
import com.carmotors.controlador.RecordatorioControlador;
import com.carmotors.vista.FacturaVista;
import com.carmotors.vista.RecordatorioVista;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class VistasEofAceptacionTest {
    @ParameterizedTest(name = "{0}: EOF {2}")
    @CsvSource(textBlock = """
            FacturaVista, '', en menú
            FacturaVista, 2, durante consulta de factura
            RecordatorioVista, '', en menú
            RecordatorioVista, 4, durante listado por vehículo
            """)
    void finDeEntradaDevuelveElControlSinCerrarElLector(
            String nombreVista, String opcion, String escenario) {
        try (var entrada = new Scanner(opcion.isEmpty() ? "" : opcion + "\n")) {
            var mostrar = abrir(nombreVista, entrada);
            assertDoesNotThrow(mostrar::run, nombreVista + ": " + escenario);
            assertFalse(entrada.hasNextLine(), "El lector del menú principal debe seguir abierto");
        }
    }

    @ParameterizedTest
    @CsvSource({"FacturaVista", "RecordatorioVista"})
    void entradasInvalidasPermitenVolverConElLectorCompartido(String nombreVista) {
        var salidaOriginal = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var entrada = new Scanner("abc\n99\n0\nCONTINUAR");
             var salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            var mostrar = abrir(nombreVista, entrada);
            System.setOut(salida);
            mostrar.run();
            assertEquals("CONTINUAR", entrada.nextLine());
        } finally {
            System.setOut(salidaOriginal);
        }
        String salida = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(salida.contains("Ingrese un número válido."));
        assertTrue(salida.contains("Opción no válida."));
    }

    @ParameterizedTest
    @CsvSource({"6, 4, RECORDATORIOS DE MANTENIMIENTO", "7, 2, FACTURACIÓN"})
    void mainManejaEofEnUnaOperacion(String ruta, String opcion, String titulo) {
        var resultado = NavegacionAceptacionTest.ejecutar(ruta, opcion);
        assertEquals(java.util.List.of("CarMotors SGT", titulo, "CarMotors SGT"), resultado.menus());
    }

    private static Runnable abrir(String nombreVista, Scanner entrada) {
        return switch (nombreVista) {
            case "FacturaVista" -> new FacturaVista(new FacturaControlador(), entrada)::mostrarMenu;
            case "RecordatorioVista" -> new RecordatorioVista(new RecordatorioControlador(), entrada)::mostrarMenu;
            default -> throw new IllegalArgumentException("Vista desconocida " + nombreVista);
        };
    }
}
