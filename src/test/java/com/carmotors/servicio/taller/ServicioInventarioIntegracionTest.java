package com.carmotors.servicio.taller;

import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.servicio.taller.ServicioService.RepuestoSolicitado;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class ServicioInventarioIntegracionTest {
    @Test
    void conservaPrecioYConsumoAlCancelarYAplicaLasClavesDeFacturacion() throws Exception {
        try (var db = EsquemaInventarioPrueba.crearConsolidado()) {
            cargarCatalogos(db);
            var servicios = servicios(db);
            int id = id(servicios, 5);
            assertEquals(5, entero(db, "SELECT cantidad_stock FROM repuestos WHERE id = 1"));
            db.ejecutar("UPDATE repuestos SET precio_venta = 18 WHERE id = 1");
            servicios.reservarRepuesto(new ReservaRepuesto(id, 1, 1));
            assertEquals(12, entero(db, "SELECT precio_unitario_venta FROM servicios_repuestos"));
            assertThrows(IllegalStateException.class, () -> servicios.consumirRepuesto(new ConsumoRepuesto(id, 1, 7)));
            servicios.consumirRepuesto(new ConsumoRepuesto(id, 1, 3));
            assertEquals(4, entero(db, "SELECT cantidad_stock FROM repuestos WHERE id = 1"));
            servicios.liberarRepuesto(new ReservaRepuesto(id, 1, 1));
            servicios.actualizarEstado(id, 2);
            servicios.actualizarEstado(id, 2);
            assertEquals(7, entero(db, "SELECT cantidad_stock FROM repuestos WHERE id = 1"));
            assertEquals(0, entero(db, "SELECT cantidad_reservada FROM servicios_repuestos"));
            assertEquals(3, entero(db, "SELECT cantidad_usada FROM servicios_repuestos"));
            assertEquals(2, entero(db, "SELECT estado_id FROM servicios WHERE id = " + id));
            assertThrows(IllegalStateException.class, () -> servicios.reservarRepuesto(new ReservaRepuesto(id, 1, 1)));

            int facturable = id(servicios, 1);
            servicios.consumirRepuesto(new ConsumoRepuesto(facturable, 1, 1));
            servicios.actualizarEstado(facturable, 3);
            String insertarFactura = """
                    INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos,
                        total_mano_obra, impuestos, total_final)
                    SELECT servicio_id, NOW(), cantidad_usada * precio_unitario_venta, 20, 0,
                        20 + cantidad_usada * precio_unitario_venta
                    FROM servicios_repuestos WHERE servicio_id =
                    """;
            db.ejecutar(insertarFactura + facturable);
            assertEquals(38, entero(db, "SELECT total_final FROM facturas"));
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> db.ejecutar(insertarFactura + facturable));
        }
    }

    @Test
    void revierteOrdenYStockSiFallaUnaReservaYRechazaVehiculoInexistente() throws Exception {
        try (var db = EsquemaInventarioPrueba.crearConsolidado()) {
            cargarCatalogos(db);
            var servicios = servicios(db);
            assertThrows(StockInsuficienteException.class, () -> servicios.registrarServicioConReservas(
                    orden(1), List.of(new RepuestoSolicitado(1, 3), new RepuestoSolicitado(2, 2))));
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> servicios.registrarServicioConReservas(orden(999), List.of(new RepuestoSolicitado(1, 1))));
            assertEquals(10, entero(db, "SELECT cantidad_stock FROM repuestos WHERE id = 1"));
            assertEquals(0, entero(db, "SELECT COUNT(*) FROM servicios"));
            assertEquals(0, entero(db, "SELECT COUNT(*) FROM servicios_repuestos"));
        }
    }

    @Test
    void dosOrdenesCompitenSinReservasParciales() throws Exception {
        try (var db = EsquemaInventarioPrueba.crearConsolidado()) {
            cargarCatalogos(db);
            var servicios = servicios(db);
            var inicio = new CountDownLatch(1);
            var hilos = Executors.newFixedThreadPool(2);
            try {
                var primera = hilos.submit(() -> reservarEnCompetencia(servicios, inicio));
                var segunda = hilos.submit(() -> reservarEnCompetencia(servicios, inicio));
                inicio.countDown();
                int exitos = primera.get(10, TimeUnit.SECONDS) + segunda.get(10, TimeUnit.SECONDS);
                assertEquals(1, exitos);
            } finally {
                hilos.shutdownNow();
            }
            assertEquals(4, entero(db, "SELECT cantidad_stock FROM repuestos WHERE id = 1"));
            assertEquals(1, entero(db, "SELECT COUNT(*) FROM servicios"));
            assertEquals(6, entero(db, "SELECT cantidad_reservada FROM servicios_repuestos"));
        }
    }

    private static int reservarEnCompetencia(ServicioService servicios, CountDownLatch inicio) throws Exception {
        inicio.await();
        try {
            id(servicios, 6);
            return 1;
        } catch (StockInsuficienteException esperado) {
            return 0;
        }
    }

    private static ServicioService servicios(EsquemaInventarioPrueba db) {
        return new ServicioService(new ServicioDAO(), new TecnicoDAO(), db::conectar);
    }

    private static int id(ServicioService servicios, int cantidad) throws Exception {
        return Math.toIntExact(servicios.registrarServicioConReservas(
                orden(1), List.of(new RepuestoSolicitado(1, cantidad))).valor());
    }

    private static Servicio orden(int vehiculoId) {
        return new Servicio(null, vehiculoId, 1, LocalDateTime.of(2026, 1, 1, 8, 0),
                null, "Mantenimiento", new BigDecimal("20.00"), 1);
    }

    private static void cargarCatalogos(EsquemaInventarioPrueba db) throws Exception {
        db.ejecutar("INSERT INTO clientes (id, nombre, documento_identidad, email) VALUES (1, 'Cliente', '123', 'cliente@example.com')");
        db.ejecutar("INSERT INTO vehiculos (id, cliente_id, placa, marca, modelo, anio) VALUES (1, 1, 'ABC123', 'Marca', 'Modelo', 2020)");
        db.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, 'Recibido'), (2, 'Cancelado'), (3, 'Completado')");
        db.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
        db.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtro')");
        db.ejecutar("""
                INSERT INTO repuestos (id, nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock)
                VALUES (1, 'Filtro A', 'Marca', 1, 12, 10), (2, 'Filtro B', 'Marca', 1, 6, 1)
                """);
    }

    private static int entero(EsquemaInventarioPrueba db, String sql) throws Exception {
        try (var conexion = db.conectar(); var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getInt(1);
        }
    }
}
