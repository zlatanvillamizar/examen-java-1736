package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Recordatorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CARMOTORS_MYSQL_AISLADO", matches = "true")
class RecordatorioJdbcTest {

    @Test
    void consultasPersistentesYUnaSolaNotificacionConcurrente() throws Exception {
        ClienteDAO clientes = new ClienteDAO();
        RecordatorioDAO recordatorios = new RecordatorioDAO();
        String token = UUID.randomUUID().toString().replace("-", "");
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente JDBC");
        cliente.setDocumentoIdentidad(token.substring(0, 20));
        cliente.setEmail(token + "@example.test");
        clientes.registrar(cliente);
        try {
            assertEquals(cliente.getId(), clientes.buscarPorDocumento(cliente.getDocumentoIdentidad())
                    .orElseThrow().getId());
            assertTrue(clientes.actualizarContacto(cliente.getId(), "123456", cliente.getEmail()));
            assertEquals("123456", clientes.buscarPorId(cliente.getId()).orElseThrow().getTelefono());
            int vehiculo = insertarVehiculo(cliente.getId(), token.substring(0, 10));
            try {
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
                    return new RecordatorioDAO().marcarNotificado(proximo.getId());
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
            } finally {
                eliminar("DELETE FROM recordatorios_mantenimiento WHERE vehiculo_id = ?", vehiculo);
                eliminar("DELETE FROM vehiculos WHERE id = ?", vehiculo);
            }
        } finally {
            eliminar("DELETE FROM clientes WHERE id = ?", cliente.getId());
        }
    }

    private int insertarVehiculo(int clienteId, String placa) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) "
                             + "VALUES (?, ?, 'Marca', 'Modelo', 2020)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, clienteId);
            ps.setString(2, placa);
            ps.executeUpdate();
            try (var claves = ps.getGeneratedKeys()) {
                assertTrue(claves.next());
                return claves.getInt(1);
            }
        }
    }

    private void eliminar(String sql, int id) throws SQLException {
        try (Connection con = ConexionFactory.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
