package com.carmotors.vista;

import com.carmotors.aceptacion.SesionMainPrueba;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.VehiculoDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
            String alta = SesionMainPrueba.ejecutar(temporal, esquema, "1\n1\nAna Pérez\nDOC-1\n\nana@example.test\n0\n0\n");
            var idGenerado = Pattern.compile("Cliente registrado con ID (\\d+)\\.").matcher(alta);
            assertTrue(idGenerado.find(), alta);
            String id = idGenerado.group(1);

            String segundaSesion = SesionMainPrueba.ejecutar(temporal, esquema, """
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
}
