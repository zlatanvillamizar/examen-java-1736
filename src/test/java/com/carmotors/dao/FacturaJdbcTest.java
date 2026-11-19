package com.carmotors.dao;

import com.carmotors.modelo.Factura;
import com.carmotors.servicio.taller.ServicioService;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import com.carmotors.servicios.facturacion.FacturaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class FacturaJdbcTest {

    @Test
    void consumoPositivoYUnaSolaFacturaConcurrenteConRollbackReal() throws Exception {
        try (var esquema = EsquemaMaryPrueba.crear()) {
            int id = crearServicio(esquema);
            agregarRepuestos(esquema, id);
            verificarEmisionConcurrente(esquema, id);
        }
    }

    private void verificarEmisionConcurrente(EsquemaMaryPrueba esquema, int id) throws Exception {
        var dao = new FacturaDAO(esquema::conectar);
        var servicio = new FacturaService(dao, new BigDecimal("0.10"));
        assertTrue(dao.buscarPorServicio(id).isEmpty());
        var datos = dao.buscarServicioFacturable(id).orElseThrow();
        assertEquals(1, datos.detalles().size());
        assertEquals(2, datos.detalles().get(0).cantidad());

        Factura factura = servicio.calcular(datos);
        try (Connection con = esquema.conectar()) {
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
        try (var con = esquema.conectar(); var st = con.createStatement();
             var filas = st.executeQuery("SELECT COUNT(*) FROM factura_detalles")) {
            assertTrue(filas.next());
            assertEquals(0, filas.getInt(1));
        }

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
        }
    }

    @Test
    void emiteServicioSinConsumo() throws SQLException, IOException {
        try (var esquema = EsquemaMaryPrueba.crear()) {
            int id = crearServicio(esquema);
            var dao = new FacturaDAO(esquema::conectar);
            var factura = new FacturaService(dao, BigDecimal.ZERO).crear(id);
            assertTrue(factura.getDetalles().isEmpty());
            assertEquals(new BigDecimal("0.00"), factura.getTotalRepuestos());
            assertEquals(factura.getTotalManoObra(), factura.getTotalFinal());
            assertEquals(factura.getId(), dao.buscarPorServicio(id).orElseThrow().getId());
        }
    }

    @Test
    void rechazaUnaEmisionDesactualizadaYCongelaElDetalleSinImpedirLiberarStock() throws Exception {
        try (var esquema = EsquemaMaryPrueba.crear()) {
            int orden = crearServicio(esquema);
            agregarRepuestos(esquema, orden);
            var dao = new FacturaDAO(esquema::conectar);
            var facturas = new FacturaService(dao, BigDecimal.ZERO);
            var servicios = new ServicioService(new ServicioDAO(), new TecnicoDAO(), esquema::conectar);
            int repuesto = dao.buscarServicioFacturable(orden).orElseThrow().detalles().get(0).repuestoId();
            try (var con = esquema.conectar(); var st = con.createStatement()) {
                st.executeUpdate("UPDATE repuestos SET cantidad_stock = 5 WHERE id = " + repuesto);
            }
            servicios.reservarRepuesto(new ReservaRepuesto(orden, repuesto, 3));
            Factura anterior = facturas.calcular(dao.buscarServicioFacturable(orden).orElseThrow());
            servicios.consumirRepuesto(new ConsumoRepuesto(orden, repuesto, 1));
            assertThrows(IllegalStateException.class, () -> dao.crear(anterior));
            assertTrue(dao.buscarPorServicio(orden).isEmpty());

            Factura emitida = facturas.crear(orden);
            var rechazo = assertThrows(IllegalStateException.class,
                    () -> servicios.consumirRepuesto(new ConsumoRepuesto(orden, repuesto, 1)));
            assertTrue(rechazo.getMessage().contains("ya está facturada"));
            servicios.liberarRepuesto(new ReservaRepuesto(orden, repuesto, 2));
            try (var con = esquema.conectar(); var st = con.createStatement()) {
                try (var filas = st.executeQuery("SELECT cantidad_stock FROM repuestos WHERE id = " + repuesto)) {
                    assertTrue(filas.next());
                    assertEquals(4, filas.getInt(1));
                }
                st.executeUpdate("UPDATE repuestos SET nombre = 'Nombre posterior' WHERE id = " + repuesto);
            }
            Factura consultada = facturas.buscarPorId(emitida.getId()).orElseThrow();
            assertEquals(emitida.getDetalles(), consultada.getDetalles());
            assertEquals(new BigDecimal("60.00"), consultada.getTotalRepuestos());
            assertEquals(new BigDecimal("160.00"), consultada.getTotalFinal());
        }
    }

    private int crearServicio(EsquemaMaryPrueba esquema) throws SQLException {
        int cliente = esquema.insertar("INSERT INTO clientes (nombre, documento_identidad, email) "
                + "VALUES ('Cliente QA', 'CC-QA', 'factura@example.test')");
        int vehiculo = esquema.insertar("INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) "
                + "VALUES (" + cliente + ", 'QA-002', 'Marca', 'Modelo', 2020)");
        int tipo = esquema.insertar("INSERT INTO mantenimiento_tipos (nombre) VALUES ('Tipo QA')");
        int estado;
        try (var con = esquema.conectar(); var st = con.createStatement();
             var filas = st.executeQuery("SELECT id FROM servicio_estados WHERE nombre = 'Completado'")) {
            estado = filas.next() ? filas.getInt(1) : esquema.insertar(
                    "INSERT INTO servicio_estados (nombre) VALUES ('Completado')");
        }
        return esquema.insertar("INSERT INTO servicios (vehiculo_id, mantenimiento_tipo_id, "
                + "fecha_recepcion, descripcion_problema, costo_mano_obra, estado_id) VALUES ("
                + vehiculo + ", " + tipo + ", NOW(), 'Frenos', 100.00, " + estado + ")");
    }

    private void agregarRepuestos(EsquemaMaryPrueba esquema, int servicio) throws SQLException {
        int tipo = esquema.insertar("INSERT INTO repuesto_tipos (nombre) VALUES ('Tipo QA')");
        int usado = esquema.insertar("INSERT INTO repuestos (nombre, marca, repuesto_tipo_id, precio_venta) "
                + "VALUES ('Filtro QA', 'Marca', " + tipo + ", 20.00)");
        int liberado = esquema.insertar("INSERT INTO repuestos (nombre, marca, repuesto_tipo_id, precio_venta) "
                + "VALUES ('Pastilla QA', 'Marca', " + tipo + ", 50.00)");
        try (var con = esquema.conectar(); var st = con.createStatement()) {
            st.executeUpdate("INSERT INTO servicios_repuestos (servicio_id, repuesto_id, "
                    + "cantidad_reservada, cantidad_usada, precio_unitario_venta) VALUES ("
                    + servicio + ", " + usado + ", 0, 2, 20.00), ("
                    + servicio + ", " + liberado + ", 0, 0, 50.00)");
        }
    }
}
