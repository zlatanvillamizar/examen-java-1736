package com.carmotors.aceptacion;

import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Isolated
class VistasEofAceptacionTest {
    @ParameterizedTest(name = "{0}: EOF {3}")
    @CsvSource(textBlock = """
            FacturaVista, FacturaControlador, '', en menú
            FacturaVista, FacturaControlador, 2, durante consulta de factura
            RecordatorioVista, RecordatorioControlador, '', en menú
            RecordatorioVista, RecordatorioControlador, 4, durante listado por vehículo
            """)
    void finDeEntradaDevuelveElControlSinCerrarElLector(
            String nombreVista, String nombreControlador, String opcion, String escenario) throws Exception {
        try (var entrada = new Scanner(opcion.isEmpty() ? "" : opcion + "\n")) {
            var mostrar = abrir(nombreVista, nombreControlador, entrada);
            assertDoesNotThrow(mostrar::ejecutar, nombreVista + ": " + escenario);
            assertFalse(entrada.hasNextLine(), "El lector del menú principal debe seguir abierto");
        }
    }

    @ParameterizedTest
    @CsvSource({"FacturaVista, FacturaControlador", "RecordatorioVista, RecordatorioControlador"})
    void entradasInvalidasPermitenVolverConElLectorCompartido(String nombreVista, String nombreControlador)
            throws Exception {
        var salidaOriginal = System.out;
        var bytes = new ByteArrayOutputStream();
        try (var entrada = new Scanner("abc\n99\n0\nCONTINUAR");
             var salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            var mostrar = abrir(nombreVista, nombreControlador, entrada);
            System.setOut(salida);
            mostrar.ejecutar();
            assertEquals("CONTINUAR", entrada.nextLine());
        } finally {
            System.setOut(salidaOriginal);
        }
        String salida = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(salida.contains("Ingrese un número válido."));
        assertTrue(salida.contains("Opción no válida."));
    }

    private Mostrar abrir(String nombreVista, String nombreControlador, Scanner entrada) throws Exception {
        var vista = ApiOpcional.clase("com.carmotors.vista." + nombreVista);
        var controlador = Class.forName("com.carmotors.controlador." + nombreControlador);
        boolean implementada = java.util.Arrays.stream(vista.getMethods())
                .anyMatch(metodo -> metodo.getName().equals("mostrarMenu") && metodo.getParameterCount() == 0);
        assumeTrue(implementada, nombreVista + " aún carece de implementación de consola");
        var instancia = vista.getConstructor(controlador, Scanner.class)
                .newInstance(controlador.getConstructor().newInstance(), entrada);
        return () -> ApiOpcional.invocar(instancia, "mostrarMenu", new Class<?>[0]);
    }

    @FunctionalInterface
    private interface Mostrar {
        void ejecutar() throws Exception;
    }
}
