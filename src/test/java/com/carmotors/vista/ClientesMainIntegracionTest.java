package com.carmotors.vista;

import com.carmotors.Main;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.VehiculoDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class ClientesMainIntegracionTest {
    @TempDir
    Path temporal;

    @Test
    void usaElIdQueMuestraMainParaRegistrarUnVehiculo() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            String alta = ejecutarMain(esquema, "1\n1\nAna Pérez\nDOC-1\n\nana@example.test\n0\n0\n");
            var idGenerado = Pattern.compile("Cliente registrado con ID (\\d+)\\.").matcher(alta);
            assertTrue(idGenerado.find(), alta);
            String id = idGenerado.group(1);

            String segundaSesion = ejecutarMain(esquema, """
                    1
                    2
                    %1$s
                    3
                    DOC-1
                    5
                    %1$s
                    3001234567
                    contacto@example.test
                    4
                    0
                    2
                    1
                    %1$s
                    ABC123
                    Mazda
                    3
                    2020
                    0
                    0
                    """.formatted(id));
            assertEquals(2, segundaSesion.lines().filter(linea -> linea.contains("Correo: ana@example.test")).count(),
                    segundaSesion);
            assertTrue(segundaSesion.contains("Contacto del cliente actualizado."), segundaSesion);
            assertTrue(segundaSesion.contains("Vehículo registrado con ID "), segundaSesion);

            int clienteId = Integer.parseInt(id);
            var cliente = new ClienteDAO(esquema::conectar).buscarPorId(clienteId).orElseThrow();
            assertEquals("3001234567", cliente.getTelefono());
            assertEquals("contacto@example.test", cliente.getEmail());
            var vehiculo = new VehiculoDAO(esquema::conectar).buscarPorPlaca("ABC123").orElseThrow();
            assertEquals(clienteId, vehiculo.getClienteId());
        }
    }

    private String ejecutarMain(EsquemaInventarioPrueba esquema, String datos) throws Exception {
        Path salida = Files.createTempFile(temporal, "main-", ".log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var proceso = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", classpath, Main.class.getName())
                .redirectErrorStream(true).redirectOutput(salida.toFile());
        String url = System.getenv(EsquemaInventarioPrueba.VARIABLE_URL);
        var destino = Pattern.compile("^(jdbc:mysql://[^/?]+)(?:/[^?]*)?(\\?.*)?$").matcher(url);
        assertTrue(destino.matches(), "Se requiere una URL JDBC MySQL TCP para la prueba de Main");
        proceso.environment().put("CARMOTORS_DB_URL", destino.replaceFirst("$1/" + esquema.nombre() + "$2"));
        proceso.environment().put("CARMOTORS_DB_USER", System.getenv().getOrDefault("CARMOTORS_TEST_DB_USER", "root"));
        proceso.environment().put("CARMOTORS_DB_PASSWORD", System.getenv().getOrDefault("CARMOTORS_TEST_DB_PASSWORD", ""));
        Process main = proceso.start();
        try {
            try (var entrada = main.getOutputStream()) {
                entrada.write(datos.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(main.waitFor(20, TimeUnit.SECONDS), "Main no terminó al recibir la opción 0");
            String resultado = Files.readString(salida, StandardCharsets.UTF_8);
            assertEquals(0, main.exitValue(), resultado);
            return resultado;
        } finally {
            if (main.isAlive()) {
                main.destroyForcibly();
                main.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
