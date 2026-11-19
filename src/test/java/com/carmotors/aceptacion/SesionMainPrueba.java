package com.carmotors.aceptacion;

import com.carmotors.Main;
import com.carmotors.dao.EsquemaInventarioPrueba;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ejecuta la aplicación real con las credenciales y el catálogo de un esquema desechable. */
public final class SesionMainPrueba {
    private SesionMainPrueba() {
    }

    public static String ejecutar(Path temporal, EsquemaInventarioPrueba esquema, String datos) throws Exception {
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
