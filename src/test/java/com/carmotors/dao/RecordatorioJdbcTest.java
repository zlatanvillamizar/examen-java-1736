package com.carmotors.dao;

import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Recordatorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class RecordatorioJdbcTest {

    @Test
    void consultasPersistentesYUnaSolaNotificacionConcurrente() throws Exception {
        try (var esquema = EsquemaMaryPrueba.crear()) {
            ClienteDAO clientes = new ClienteDAO(esquema::conectar);
            RecordatorioDAO recordatorios = new RecordatorioDAO(esquema::conectar);
            Cliente cliente = clientes.registrar(
                    new Cliente("Cliente JDBC", "CC-JDBC", null, "recordatorio@example.test"));
            assertEquals(cliente.getId(), clientes.buscarPorDocumento(cliente.getDocumentoIdentidad())
                    .orElseThrow().getId());
            assertTrue(clientes.actualizarContacto(cliente.getId(), "123456", cliente.getEmail()));
            assertEquals("123456", clientes.buscarPorId(cliente.getId()).orElseThrow().getTelefono());
            int vehiculo = esquema.insertar("INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) "
                    + "VALUES (" + cliente.getId() + ", 'QA-001', 'Marca', 'Modelo', 2020)");
            LocalDate hoy = LocalDate.now();
            Recordatorio vencido = recordatorios.crear(new Recordatorio(vehiculo, null,
                    hoy.minusDays(1), "Vencido"));
            Recordatorio proximo = recordatorios.crear(new Recordatorio(vehiculo, null,
                    hoy.plusDays(1), "Próximo"));
            assertFalse(recordatorios.buscarPorId(proximo.getId()).orElseThrow().isNotificado());
            assertTrue(recordatorios.listarVencidos(hoy).stream()
                    .anyMatch(r -> r.getId() == vencido.getId()));
            assertTrue(recordatorios.listarProximos(hoy, hoy.plusDays(1)).stream()
                    .anyMatch(r -> r.getId() == proximo.getId()));

            var pool = Executors.newFixedThreadPool(2);
            var inicio = new CountDownLatch(1);
            Callable<Boolean> notificar = () -> {
                assertTrue(inicio.await(5, TimeUnit.SECONDS));
                return new RecordatorioDAO(esquema::conectar).marcarNotificado(proximo.getId());
            };
            try {
                var primero = pool.submit(notificar);
                var segundo = pool.submit(notificar);
                inicio.countDown();
                assertNotEquals(primero.get(10, TimeUnit.SECONDS),
                        segundo.get(10, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }

            assertFalse(recordatorios.marcarNotificado(proximo.getId()));
            assertTrue(recordatorios.buscarPorId(proximo.getId()).orElseThrow().isNotificado());
            assertTrue(recordatorios.listarPendientes().stream()
                    .noneMatch(r -> r.getId() == proximo.getId()));
            assertEquals(List.of(vencido.getId()), recordatorios
                    .listarAlertasPorVehiculo(vehiculo, hoy.plusDays(1)).stream()
                    .map(Recordatorio::getId).toList());
            assertThrows(SQLException.class, () -> recordatorios.crear(
                    new Recordatorio(vehiculo, -1, hoy, "Servicio inexistente")));
            assertEquals(2, recordatorios.listarPorVehiculo(vehiculo).size());
        }
    }
}
