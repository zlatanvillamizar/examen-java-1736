package com.carmotors.dao;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.clientes.ClienteService;
import com.carmotors.servicios.vehiculos.VehiculoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class ClienteDAOIntegracionTest {
    @Test
    void registraConsultaYActualizaClienteYRegistraSuVehiculo() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            var clientes = controlador(esquema);
            Cliente creado = clientes.registrar("  Ana Pérez  ", "  DOC-1  ", "", "ANA@EXAMPLE.TEST");
            assertTrue(creado.getId() > 0);
            int id = creado.getId();

            var nuevaSesion = controlador(esquema);
            Cliente encontrado = nuevaSesion.buscarPorDocumento("DOC-1").orElseThrow();
            assertEquals(id, encontrado.getId());
            assertEquals("Ana Pérez", encontrado.getNombre());
            assertNull(encontrado.getTelefono());
            assertEquals("ana@example.test", encontrado.getEmail());
            assertEquals(List.of(id), nuevaSesion.listar().stream().map(Cliente::getId).toList());
            assertTrue(nuevaSesion.actualizarContacto(id, "3001234567", "otro@example.test"));
            assertFalse(nuevaSesion.actualizarContacto(id + 1, null, "ausente@example.test"));
            Cliente actualizado = controlador(esquema).buscarPorId(id).orElseThrow();
            assertEquals("3001234567", actualizado.getTelefono());
            assertEquals("otro@example.test", actualizado.getEmail());

            var vehiculos = new VehiculoControlador(new VehiculoService(
                    new VehiculoDAO(esquema::conectar), new ClienteDAO(esquema::conectar)));
            Vehiculo vehiculo = vehiculos.registrarVehiculo(new Vehiculo(id, "ABC123", "Mazda", "3", 2020));
            assertEquals(id, vehiculos.buscarVehiculo(vehiculo.getId()).orElseThrow().getClienteId());
        }
    }

    @Test
    void rechazaDuplicadosYConservaLosDatosDelCliente() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            var clientes = controlador(esquema);
            Cliente primero = clientes.registrar("Ana", "DOC-1", null, "ana@example.test");
            assertThrows(IllegalStateException.class,
                    () -> clientes.registrar("Otra Ana", "DOC-1", null, "otra@example.test"));
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> clientes.registrar("Otra Ana", "DOC-2", null, "ana@example.test"));
            Cliente segundo = clientes.registrar("Luis", "DOC-3", null, "luis@example.test");
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> clientes.actualizarContacto(segundo.getId(), "3001234567", "ana@example.test"));

            var nuevaSesion = controlador(esquema);
            assertEquals(2, nuevaSesion.listar().size());
            assertEquals(primero.getId(), nuevaSesion.buscarPorDocumento("DOC-1").orElseThrow().getId());
            assertTrue(nuevaSesion.buscarPorDocumento("DOC-2").isEmpty());
            Cliente sinCambios = nuevaSesion.buscarPorId(segundo.getId()).orElseThrow();
            assertEquals("luis@example.test", sinCambios.getEmail());
            assertNull(sinCambios.getTelefono());
        }
    }

    private static ClienteControlador controlador(EsquemaInventarioPrueba esquema) {
        return new ClienteControlador(new ClienteService(new ClienteDAO(esquema::conectar)));
    }
}
