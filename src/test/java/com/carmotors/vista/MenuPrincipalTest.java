package com.carmotors.vista;

import com.carmotors.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class MenuPrincipalTest {
    @Test
    void navegaDesdeMainPorVehiculosServiciosTecnicosYSale() {
        String salida = ejecutarMain("x\n2\nx\n2\n0\n0\n3\n8\n0\n0\n0\n");
        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 7."));
        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 4."));
        assertTrue(salida.contains("ID del vehículo debe ser positivo."));
        int vehiculos = salida.indexOf("=== Vehículos ===");
        int servicios = salida.indexOf("=== Servicios ===");
        int tecnicos = salida.indexOf("=== Técnicos ===");
        assertTrue(vehiculos >= 0 && servicios > vehiculos && tecnicos > servicios, salida);
        assertEquals(4L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
    }

    @Test
    void terminaSiLaEntradaAcabaEnVehiculos() {
        assertTrue(ejecutarMain("2\n1\n").contains("ID del cliente: "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "4", "5", "6", "7"})
    void informaQueLaVistaEstaPendienteYRegresaAlMenu(String opcion) {
        String salida = ejecutarMain(opcion + "\n0\n");
        assertTrue(salida.contains("La vista de este módulo está pendiente de integración."));
        assertEquals(2L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
    }

    private String ejecutarMain(String datos) {
        InputStream entradaOriginal = System.in;
        PrintStream salidaOriginal = System.out;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PrintStream impresion = new PrintStream(salida, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream(datos.getBytes(StandardCharsets.UTF_8)));
            System.setOut(impresion);
            Main.main(new String[0]);
        } finally {
            System.setIn(entradaOriginal);
            System.setOut(salidaOriginal);
        }
        return salida.toString(StandardCharsets.UTF_8);
    }
}
