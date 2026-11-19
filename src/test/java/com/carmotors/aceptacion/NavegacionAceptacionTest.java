package com.carmotors.aceptacion;

import com.carmotors.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class NavegacionAceptacionTest {
    private static final String PRINCIPAL = "CarMotors SGT";
    private static final String SERVICIOS = "Servicios";
    private static final String TECNICOS = "Técnicos";
    private static final Pattern ENCABEZADO = Pattern.compile("=== ([^=\\r\\n]+) ===");

    @Test
    void vuelveDeDosNivelesReentraYSaleConLaMismaEntrada() {
        String servicios = opcionServicios();
        Resultado resultado = ejecutar("inválida", servicios, "inválida", "8", "inválida",
                "1", "abc", "0", "0", servicios, "0", "0");
        assertEquals(List.of(PRINCIPAL, PRINCIPAL, SERVICIOS, SERVICIOS, TECNICOS, TECNICOS,
                TECNICOS, SERVICIOS, PRINCIPAL, SERVICIOS, PRINCIPAL), resultado.menus(), resultado.salida());
        assertEquals(3, Pattern.compile("Opción inválida").matcher(resultado.salida()).results().count());
        assertTrue(resultado.salida().contains("ID del técnico debe ser un número entero."));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ''      | CarMotors SGT
            S       | CarMotors SGT,Servicios,CarMotors SGT
            S,8     | CarMotors SGT,Servicios,Técnicos,CarMotors SGT
            S,8,1   | CarMotors SGT,Servicios,Técnicos,CarMotors SGT
            """)
    void terminaAlAgotarseLaEntradaEnElMenuOUnaOperacion(String guion, String menus) {
        Resultado resultado = guion.isEmpty() ? ejecutar() : ejecutar(guion.replace("S", opcionServicios()).split(","));
        assertEquals(List.of(menus.split(",")), resultado.menus(), resultado.salida());
    }

    static String opcionServicios() {
        var opcion = Pattern.compile("(?m)^([0-9]+)\\. Servicios(?: y Técnicos)?$")
                .matcher(ejecutar("0").salida());
        assertTrue(opcion.find(), "El menú principal debe anunciar Servicios");
        return opcion.group(1);
    }

    static Resultado ejecutar(String... lineas) {
        var entradaOriginal = System.in;
        var salidaOriginal = System.out;
        var entrada = new EntradaConSeguimiento(String.join("\n", lineas));
        var bytes = new ByteArrayOutputStream();
        try (var salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setIn(entrada);
            System.setOut(salida);
            Main.main(new String[0]);
        } finally {
            System.setIn(entradaOriginal);
            System.setOut(salidaOriginal);
        }
        assertTrue(entrada.cierres <= 1, "La aplicación no debe cerrar la entrada varias veces");
        return new Resultado(bytes.toString(StandardCharsets.UTF_8));
    }

    record Resultado(String salida) {
        List<String> menus() {
            return ENCABEZADO.matcher(salida).results().map(resultado -> resultado.group(1)).toList();
        }
    }

    private static final class EntradaConSeguimiento extends ByteArrayInputStream {
        private int cierres;

        EntradaConSeguimiento(String lineas) {
            super(lineas.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public synchronized int read(byte[] buffer, int desplazamiento, int longitud) {
            assertEquals(0, cierres, "Un submenú cerró la entrada antes de que Main terminara");
            return super.read(buffer, desplazamiento, longitud);
        }

        @Override
        public void close() {
            cierres++;
        }
    }
}
