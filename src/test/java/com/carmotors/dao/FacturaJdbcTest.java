package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Factura;
import com.carmotors.servicios.facturacion.FacturaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CARMOTORS_MYSQL_AISLADO", matches = "true")
class FacturaJdbcTest {

    @Test
    void consumoPositivoYUnaSolaFacturaConcurrenteConRollbackReal() throws Exception {
        int id = servicioDePrueba("CARMOTORS_SERVICIO_CONSUMO_MIXTO");
        var dao = new FacturaDAO();
        var servicio = new FacturaService(dao, new BigDecimal("0.10"));
        assertTrue(dao.buscarPorServicio(id).isEmpty());
        var datos = dao.buscarServicioFacturable(id).orElseThrow();
        assertEquals(1, datos.detalles().size());
        assertEquals(2, datos.detalles().get(0).cantidad());
        verificarFixtureMixta(id);

        Factura factura = servicio.calcular(datos);
        try (Connection con = ConexionFactory.obtenerConexion()) {
            SQLException fallaCommit = new SQLException("Fallo de commit inyectado tras INSERT");
            Connection conexion = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                        if (metodo.getName().equals("commit")) throw fallaCommit;
                        try {
                            return metodo.invoke(con, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
            assertSame(fallaCommit, assertThrows(SQLException.class,
                    () -> dao.insertarEnTransaccion(conexion, factura)));
            assertTrue(con.getAutoCommit());
        }
        assertEquals(0, factura.getId());
        assertTrue(dao.buscarPorServicio(id).isEmpty());

        var pool = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        Callable<Boolean> emitir = () -> {
            assertTrue(inicio.await(5, TimeUnit.SECONDS));
            try {
                servicio.crear(id);
                return true;
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("ya tiene factura"));
                return false;
            } catch (SQLException e) {
                assertEquals(1062, e.getErrorCode());
                return false;
            }
        };
        try {
            var primera = pool.submit(emitir);
            var segunda = pool.submit(emitir);
            inicio.countDown();
            assertNotEquals(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
            Factura persistida = servicio.buscarPorId(dao.buscarPorServicio(id).orElseThrow().getId())
                    .orElseThrow();
            assertEquals(1, persistida.getDetalles().size());
            assertEquals(new BigDecimal("40.00"), persistida.getTotalRepuestos());
            assertEquals(new BigDecimal("100.00"), persistida.getTotalManoObra());
            assertEquals(new BigDecimal("14.00"), persistida.getImpuestos());
            assertEquals(new BigDecimal("154.00"), persistida.getTotalFinal());
            assertEquals(1062, assertThrows(SQLException.class, () -> dao.crear(factura)).getErrorCode());
            assertEquals(1, dao.listar().stream().filter(f -> f.getServicioId() == id).count());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            eliminarFacturaDePrueba(id);
        }
    }

    @Test
    void emiteServicioSinConsumo() throws SQLException {
        int id = servicioDePrueba("CARMOTORS_SERVICIO_SIN_CONSUMO");
        var dao = new FacturaDAO();
        assertTrue(dao.buscarPorServicio(id).isEmpty());
        try {
            var factura = new FacturaService(dao, BigDecimal.ZERO).crear(id);
            assertTrue(factura.getDetalles().isEmpty());
            assertEquals(new BigDecimal("0.00"), factura.getTotalRepuestos());
            assertEquals(factura.getTotalManoObra(), factura.getTotalFinal());
            assertEquals(factura.getId(), dao.buscarPorServicio(id).orElseThrow().getId());
        } finally {
            eliminarFacturaDePrueba(id);
        }
    }

    private int servicioDePrueba(String variable) {
        return Integer.parseInt(assertDoesNotThrow(() ->
                java.util.Objects.requireNonNull(System.getenv(variable), variable)));
    }

    private void verificarFixtureMixta(int id) throws SQLException {
        try (var con = ConexionFactory.obtenerConexion();
             var ps = con.prepareStatement("SELECT COUNT(*) FROM servicios_repuestos "
                     + "WHERE servicio_id = ? AND cantidad_usada = 0")) {
            ps.setInt(1, id);
            try (var filas = ps.executeQuery()) {
                assertTrue(filas.next());
                assertTrue(filas.getInt(1) > 0, "El servicio debe tener un repuesto de consumo cero");
            }
        }
    }

    private void eliminarFacturaDePrueba(int id) throws SQLException {
        try (var con = ConexionFactory.obtenerConexion();
             var ps = con.prepareStatement("DELETE FROM facturas WHERE servicio_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
