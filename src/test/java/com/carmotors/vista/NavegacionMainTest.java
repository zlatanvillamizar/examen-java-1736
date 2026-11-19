package com.carmotors.vista;

import com.carmotors.Main;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NavegacionMainTest {
    @Test
    void vuelveDeLosSubmenusYManejaEntradaInvalida() {
        var entradaOriginal = System.in;
        var salidaOriginal = System.out;
        var captura = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream("x\n2\n0\n3\n8\n0\n0\n7\n0\n"
                    .getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            Main.main(new String[0]);
        } finally {
            System.setIn(entradaOriginal);
            System.setOut(salidaOriginal);
        }
        String salida = captura.toString(StandardCharsets.UTF_8);
        assertTrue(salida.contains("Opción inválida"));
        assertTrue(salida.contains("=== Vehículos"));
        assertTrue(salida.contains("=== Técnicos"));
        assertTrue(salida.contains("La vista de este módulo está pendiente de integración."));
    }
}
