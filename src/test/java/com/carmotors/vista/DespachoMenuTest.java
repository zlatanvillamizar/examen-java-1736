package com.carmotors.vista;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DespachoMenuTest {
    @ParameterizedTest
    @CsvSource({"6, Recordatorios y Alertas", "7, Facturación"})
    void abreVistaConEntradaCompartidaYRegresaConCero(String opcion, String nombre) {
        String resultado = ejecutar(opcion, nombre, opcion + "\nx\n0\n0\n");
        int submenu = resultado.indexOf("Vista simulada: " + nombre);
        int invalida = resultado.indexOf("Opción inválida en la vista");
        int regreso = resultado.lastIndexOf("=== CarMotors SGT ===");
        assertTrue(submenu >= 0 && invalida > submenu && regreso > invalida, resultado);
        assertFalse(resultado.contains("pendiente de integración"), resultado);
    }

    @ParameterizedTest
    @CsvSource({"6, Recordatorios y Alertas", "7, Facturación"})
    void terminaSiLaEntradaAcabaDentroDeLaVista(String opcion, String nombre) {
        String resultado = ejecutar(opcion, nombre, opcion + "\n");
        assertTrue(resultado.contains("Vista simulada: " + nombre), resultado);
        assertFalse(resultado.contains("Opción inválida"), resultado);
    }

    private static String ejecutar(String opcion, String nombre, String datos) {
        var captura = new ByteArrayOutputStream();
        try (var entrada = new Scanner(datos);
             var salida = new PrintStream(captura, true, StandardCharsets.UTF_8)) {
            new MenuPrincipal().mostrar(entrada, salida, Map.of(opcion, () -> {
                salida.println("Vista simulada: " + nombre);
                while (entrada.hasNextLine()) {
                    if ("0".equals(entrada.nextLine().strip())) {
                        return;
                    }
                    salida.println("Opción inválida en la vista");
                }
            }));
        }
        return captura.toString(StandardCharsets.UTF_8);
    }
}
