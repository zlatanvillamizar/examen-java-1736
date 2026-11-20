package com.carmotors.vista;

import com.carmotors.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    @CsvSource({"6, RECORDATORIOS DE MANTENIMIENTO", "7, FACTURACIÓN"})
    void abreRecordatoriosYFacturacionYRegresaAlMenu(String opcion, String titulo) {
        String salida = ejecutarMain(opcion + "\nx\n99\n0\n2\n0\n0\n");
        int vista = salida.indexOf("=== " + titulo + " ===");
        assertTrue(vista >= 0 && salida.indexOf("=== Vehículos ===") > vista, salida);
        assertTrue(salida.contains("Ingrese un número válido."), salida);
        assertTrue(salida.contains("Opción no válida."), salida);
        assertFalse(salida.contains("pendiente de integración"), salida);
    }

    @ParameterizedTest
    @CsvSource({"1, Clientes, 5", "4, Inventario, 8", "5, Proveedores, 5"})
    void abreVistaRealRechazaOpcionInvalidaYRegresa(String opcion, String titulo, int maximo) {
        String salida = ejecutarMain(opcion + "\nx\n0\n2\n0\n0\n");
        int vista = salida.indexOf("=== " + titulo + " ===");
        int invalida = salida.indexOf("Opción inválida. Elija un número entre 0 y " + maximo + ".");
        int vehiculos = salida.indexOf("=== Vehículos ===");
        assertTrue(vista >= 0 && invalida > vista && vehiculos > invalida, salida);
        assertEquals(3L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
        assertFalse(salida.contains("pendiente de integración"), salida);
    }

    @ParameterizedTest
    @CsvSource({"1, Clientes", "4, Inventario", "5, Proveedores"})
    void terminaConEofEnLaVistaRealOEnUnRegistro(String opcion, String titulo) {
        assertTrue(ejecutarMain(opcion + "\n").contains("=== " + titulo + " ==="));
        String salida = ejecutarMain(opcion + "\n1\n");
        assertTrue(salida.contains("Nombre: "), salida);
        assertFalse(salida.contains("registrado con ID"), salida);
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
