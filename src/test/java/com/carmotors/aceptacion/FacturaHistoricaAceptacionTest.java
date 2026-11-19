package com.carmotors.aceptacion;

import com.carmotors.Main;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.mysql.cj.jdbc.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class FacturaHistoricaAceptacionTest {
    @Test
    void elDetalleConsultadoSigueCoincidiendoConLaFacturaEmitida() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of("database", "esquema", "esquema.sql")),
                "La prueba requiere el esquema consolidado y las rutas integradas");
        try (var esquema = (EsquemaInventarioPrueba) EsquemaInventarioPrueba.class
                .getMethod("crearConsolidado").invoke(null)) {
            esquema.ejecutar("INSERT INTO clientes (id, nombre, documento_identidad, email) "
                    + "VALUES (1, 'Cliente QA', 'QA-1', 'qa@example.test')");
            esquema.ejecutar("INSERT INTO vehiculos (id, cliente_id, placa, marca, modelo, anio) "
                    + "VALUES (1, 1, 'QA1001', 'Mazda', '3', 2020)");
            esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, 'Recibido'), (2, 'Completado')");
            esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
            esquema.ejecutar("INSERT INTO repuestos (id, nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock) "
                    + "VALUES (1, 'FiltroQA', 'Marca', 1, 10.25, 10)");

            String emision = main(esquema, """
                    3
                    1
                    1
                    1
                    2026-11-19 12:00

                    Mantenimiento QA
                    20.15
                    1
                    9
                    1
                    1
                    5
                    10
                    1
                    1
                    2
                    4
                    1
                    2
                    0
                    7
                    1
                    1
                    0
                    0
                    """);
            assertTrue(emision.contains("FACTURA 1"), emision);
            String detalleOriginal = detalle(emision);
            assertTrue(detalleOriginal.endsWith("20.50"), detalleOriginal);
            String consulta = main(esquema, """
                    3
                    10
                    1
                    1
                    1
                    0
                    7
                    2
                    1
                    0
                    0
                    """);
            assertTrue(consulta.contains("FACTURA 1"), consulta);
            assertTrue(consulta.matches("(?s).*Repuestos:\\s+20\\.50.*TOTAL:\\s+40\\.65.*"), consulta);
            assertEquals(detalleOriginal, detalle(consulta),
                    "El detalle histórico de la factura debe coincidir con sus importes persistidos");
        }
    }

    private static String detalle(String salida) {
        return salida.lines().filter(linea -> linea.startsWith("FiltroQA")).findFirst()
                .orElseThrow(() -> new AssertionError("La factura no muestra el detalle esperado:\n" + salida));
    }

    private static String main(EsquemaInventarioPrueba esquema, String entrada) throws Exception {
        String url = System.getenv(EsquemaInventarioPrueba.VARIABLE_URL);
        assertTrue(url.matches("jdbc:mysql://[^/]+/"), "Usa una URL de MySQL de prueba sin catálogo ni parámetros");
        String classpath = ubicacion(Main.class) + File.pathSeparator + ubicacion(Driver.class);
        var comando = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, Main.class.getName()).redirectErrorStream(true);
        comando.environment().put("CARMOTORS_DB_URL", url + esquema.nombre());
        comando.environment().put("CARMOTORS_DB_USER",
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"));
        comando.environment().put("CARMOTORS_DB_PASSWORD",
                System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
        var proceso = comando.start();
        try {
            try (var teclado = proceso.getOutputStream()) {
                teclado.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(proceso.waitFor(15, TimeUnit.SECONDS), "Main debe finalizar al salir");
            String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, proceso.exitValue(), salida);
            return salida;
        } finally {
            if (proceso.isAlive()) {
                proceso.destroyForcibly();
            }
        }
    }

    private static String ubicacion(Class<?> clase) throws URISyntaxException {
        return Path.of(clase.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
}
