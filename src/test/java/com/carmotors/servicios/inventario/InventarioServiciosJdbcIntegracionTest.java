package com.carmotors.servicios.inventario;

import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.RepuestoNoEncontradoException;
import com.carmotors.dao.StockInsuficienteException;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integración Inventario–Servicios contra MySQL real, con las tablas reales de
 * {@code database/bloques/} en una base desechable (ver {@link EsquemaInventarioPrueba}).
 * Solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class InventarioServiciosJdbcIntegracionTest {

    private static final int STOCK_INICIAL = 10;
    private static final int FILTRO = 1;
    private static final int BUJIA = 2;
    private static final int SERVICIO_A = 1;
    private static final int SERVICIO_B = 2;
    private static final int SERVICIO_INEXISTENTE = 999;

    private EsquemaInventarioPrueba esquema;

    @BeforeEach
    void crearBaseDesechable() throws SQLException, IOException {
        esquema = EsquemaInventarioPrueba.crearConServicios();
        esquema.ejecutar("INSERT INTO repuesto_tipos (nombre) VALUES ('Filtros')");
        esquema.ejecutar("INSERT INTO repuestos (nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock, stock_minimo) "
                + "VALUES ('Filtro de aceite', 'Bosch', 1, 25000.00, " + STOCK_INICIAL + ", 2), "
                + "('Bujía', 'NGK', 1, 12000.00, " + STOCK_INICIAL + ", 2)");
        esquema.ejecutar("INSERT INTO clientes (nombre, documento_identidad, telefono, email) "
                + "VALUES ('Cliente Prueba', '100', '300', 'cliente@prueba.co')");
        esquema.ejecutar("INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) VALUES (1, 'ABC123', 'Mazda', '3', 2020)");
        esquema.ejecutar("INSERT INTO servicio_estados (nombre) VALUES ('Recibido')");
        esquema.ejecutar("INSERT INTO mantenimiento_tipos (nombre) VALUES ('Preventivo')");
        esquema.ejecutar("INSERT INTO servicios (vehiculo_id, mantenimiento_tipo_id, fecha_recepcion, descripcion_problema, estado_id) "
                + "VALUES (1, 1, '2026-11-19 08:00:00', 'Cambio de aceite', 1), "
                + "(1, 1, '2026-11-19 09:00:00', 'Revisión de frenos', 1)");
    }

    @AfterEach
    void eliminarBaseDesechable() throws SQLException {
        if (esquema != null) {
            esquema.close();
        }
    }

    // ---------- utilidades ----------

    private <T> T transaccion(InventarioServiciosJdbc.OperacionConInventario<T> operacion) throws SQLException {
        return InventarioServiciosJdbc.ejecutarEnTransaccion(esquema::conectar, operacion);
    }

    private void reservar(int servicio, int repuesto, int cantidad) throws SQLException {
        transaccion((c, inv) -> {
            inv.reservar(new ReservaRepuesto(servicio, repuesto, cantidad));
            return null;
        });
    }

    private void consumir(int servicio, int repuesto, int cantidad) throws SQLException {
        transaccion((c, inv) -> {
            inv.consumir(new ConsumoRepuesto(servicio, repuesto, cantidad));
            return null;
        });
    }

    private void liberar(int servicio, int repuesto, int cantidad) throws SQLException {
        transaccion((c, inv) -> {
            inv.liberar(new ReservaRepuesto(servicio, repuesto, cantidad));
            return null;
        });
    }

    private int stock(int repuesto) throws SQLException {
        return entero("SELECT cantidad_stock FROM repuestos WHERE id = " + repuesto);
    }

    /** {reservada, usada}, o {-1, -1} si la línea no existe. */
    private int[] linea(int servicio, int repuesto) throws SQLException {
        try (Connection c = esquema.conectar(); PreparedStatement s = c.prepareStatement(
                "SELECT cantidad_reservada, cantidad_usada FROM servicios_repuestos WHERE servicio_id = ? AND repuesto_id = ?")) {
            s.setInt(1, servicio);
            s.setInt(2, repuesto);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new int[]{r.getInt(1), r.getInt(2)} : new int[]{-1, -1};
            }
        }
    }

    private BigDecimal precioLinea(int servicio, int repuesto) throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT precio_unitario_venta FROM servicios_repuestos WHERE servicio_id = " + servicio
                        + " AND repuesto_id = " + repuesto)) {
            r.next();
            return r.getBigDecimal(1);
        }
    }

    private int entero(String sql) throws SQLException {
        try (Connection c = esquema.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    /** Unidades físicas: en bodega + reservadas pendientes + ya usadas en servicios. */
    private int totalFisico(int repuesto) throws SQLException {
        return stock(repuesto) + entero("SELECT COALESCE(SUM(cantidad_reservada + cantidad_usada), 0) "
                + "FROM servicios_repuestos WHERE repuesto_id = " + repuesto);
    }

    // ---------- reservas ----------

    @Test
    void unaReservaValidaDescuentaElStockUnaVezYCreaLaLineaConElPrecioVigente() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 3);

        assertEquals(7, stock(FILTRO));
        assertEquals(3, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(0, linea(SERVICIO_A, FILTRO)[1]);
        assertEquals(new BigDecimal("25000.00"), precioLinea(SERVICIO_A, FILTRO));
    }

    @Test
    void reservasSucesivasSumanAlSaldoYConservanElPrecioDeLaLinea() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 2);
        esquema.ejecutar("UPDATE repuestos SET precio_venta = 30000.00 WHERE id = " + FILTRO);
        reservar(SERVICIO_A, FILTRO, 3);

        assertEquals(5, stock(FILTRO));
        assertEquals(5, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(new BigDecimal("25000.00"), precioLinea(SERVICIO_A, FILTRO));
    }

    @Test
    void conStockInsuficienteSeRechazaLaReservaCompletaSinCambios() throws SQLException {
        assertThrows(StockInsuficienteException.class, () -> reservar(SERVICIO_A, FILTRO, STOCK_INICIAL + 1));

        assertEquals(STOCK_INICIAL, stock(FILTRO));
        assertEquals(-1, linea(SERVICIO_A, FILTRO)[0], "No debía crearse la línea");
    }

    @Test
    void unaReservaNoPuedeUsarUnidadesYaComprometidasPorOtroServicio() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 8);

        assertThrows(StockInsuficienteException.class, () -> reservar(SERVICIO_B, FILTRO, 3));
        reservar(SERVICIO_B, FILTRO, 2);

        assertEquals(0, stock(FILTRO));
        assertEquals(8, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(2, linea(SERVICIO_B, FILTRO)[0]);
    }

    @Test
    void reservarUnRepuestoInexistenteFallaSinCambios() {
        assertThrows(RepuestoNoEncontradoException.class, () -> reservar(SERVICIO_A, 99, 1));
    }

    @Test
    void reservarParaUnServicioInexistenteHaceRollbackDelDescuentoDeStock() throws SQLException {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reservar(SERVICIO_INEXISTENTE, FILTRO, 4));

        assertInstanceOf(SQLIntegrityConstraintViolationException.class, error.getCause());
        assertEquals(STOCK_INICIAL, stock(FILTRO), "El stock ya descontado debía revertirse");
    }

    // ---------- consumos ----------

    @Test
    void unConsumoParcialPasaUnidadesAUsadasSinVolverADescontarStock() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 5);
        consumir(SERVICIO_A, FILTRO, 2);

        assertEquals(5, stock(FILTRO), "Consumir no debe descontar otra vez");
        assertEquals(3, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(2, linea(SERVICIO_A, FILTRO)[1]);

        consumir(SERVICIO_A, FILTRO, 3);
        assertEquals(5, stock(FILTRO));
        assertEquals(0, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(5, linea(SERVICIO_A, FILTRO)[1]);
    }

    @Test
    void noSePuedeConsumirMasQueLaReservaPendiente() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 2);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> consumir(SERVICIO_A, FILTRO, 3));

        assertTrue(error.getMessage().contains("reserva pendiente (2)"));
        assertEquals(2, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(0, linea(SERVICIO_A, FILTRO)[1]);
        assertEquals(8, stock(FILTRO));
    }

    @Test
    void consumirSinReservaFalla() {
        assertThrows(NoSuchElementException.class, () -> consumir(SERVICIO_A, FILTRO, 1));
    }

    // ---------- liberaciones y cancelaciones ----------

    @Test
    void liberarDevuelveSoloLoNoConsumidoYNoAlteraLoUsado() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 5);
        consumir(SERVICIO_A, FILTRO, 2);
        liberar(SERVICIO_A, FILTRO, 3);

        assertEquals(8, stock(FILTRO), "10 − 5 reservadas + 3 liberadas");
        assertEquals(0, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(2, linea(SERVICIO_A, FILTRO)[1], "Lo consumido no cambia");
    }

    @Test
    void noSePuedeLiberarMasQueElSaldoPendiente() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 5);
        consumir(SERVICIO_A, FILTRO, 4);

        assertThrows(IllegalStateException.class, () -> liberar(SERVICIO_A, FILTRO, 2));

        assertEquals(5, stock(FILTRO));
        assertEquals(1, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(4, linea(SERVICIO_A, FILTRO)[1]);
    }

    @Test
    void liberarSinReservaFalla() {
        assertThrows(NoSuchElementException.class, () -> liberar(SERVICIO_A, FILTRO, 1));
    }

    @Test
    void ejemploAprobadoD1ReservarCincoConsumirDosYLiberarElResto() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 5);
        assertArrayEquals(new int[]{5, 0}, linea(SERVICIO_A, FILTRO));
        assertEquals(5, stock(FILTRO));

        consumir(SERVICIO_A, FILTRO, 2);
        assertArrayEquals(new int[]{3, 2}, linea(SERVICIO_A, FILTRO));
        assertEquals(5, stock(FILTRO), "Consumir no vuelve a descontar el stock");

        liberar(SERVICIO_A, FILTRO, 3);
        assertArrayEquals(new int[]{0, 2}, linea(SERVICIO_A, FILTRO));
        assertEquals(STOCK_INICIAL - 2, stock(FILTRO), "Solo quedan fuera las 2 unidades usadas");
    }

    @Test
    void cancelarUnServicioLiberaTodoSuSaldoSinTocarLoUsadoNiOtrosServicios() throws SQLException {
        reservar(SERVICIO_A, FILTRO, 4);
        reservar(SERVICIO_A, BUJIA, 3);
        consumir(SERVICIO_A, FILTRO, 1);
        reservar(SERVICIO_B, FILTRO, 2);

        int devueltas = transaccion((c, inv) -> inv.liberarTodo(SERVICIO_A));

        assertEquals(6, devueltas, "3 filtros + 3 bujías pendientes");
        assertEquals(STOCK_INICIAL - 1 - 2, stock(FILTRO), "Queda descontado lo usado y lo reservado por B");
        assertEquals(STOCK_INICIAL, stock(BUJIA));
        assertEquals(0, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(1, linea(SERVICIO_A, FILTRO)[1]);
        assertEquals(2, linea(SERVICIO_B, FILTRO)[0], "Las reservas de otro servicio no cambian");
    }

    @Test
    void cancelarUnServicioSinReservasNoCambiaNada() throws SQLException {
        assertEquals(0, (int) transaccion((c, inv) -> inv.liberarTodo(SERVICIO_A)));
        assertEquals(STOCK_INICIAL, stock(FILTRO));
    }

    // ---------- atomicidad con la transacción de Servicios ----------

    @Test
    void siFallaUnaOperacionSeDeshaceTodaLaTransaccionDeNegocio() throws SQLException {
        assertThrows(StockInsuficienteException.class, () -> transaccion((c, inv) -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE servicios SET costo_mano_obra = 50000 WHERE id = " + SERVICIO_A);
            }
            inv.reservar(new ReservaRepuesto(SERVICIO_A, FILTRO, 4));
            inv.reservar(new ReservaRepuesto(SERVICIO_A, BUJIA, STOCK_INICIAL + 5));
            return null;
        }));

        assertEquals(STOCK_INICIAL, stock(FILTRO), "La primera reserva debía revertirse");
        assertEquals(STOCK_INICIAL, stock(BUJIA));
        assertEquals(-1, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(0, entero("SELECT COUNT(*) FROM servicios WHERE costo_mano_obra IS NOT NULL"),
                "El cambio propio de Servicios en la misma conexión también debía revertirse");
    }

    @Test
    void unaTransaccionCorrectaConfirmaLosCambiosDeAmbosModulos() throws SQLException {
        transaccion((c, inv) -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("UPDATE servicios SET costo_mano_obra = 50000 WHERE id = " + SERVICIO_A);
            }
            inv.reservar(new ReservaRepuesto(SERVICIO_A, FILTRO, 2));
            inv.consumir(new ConsumoRepuesto(SERVICIO_A, FILTRO, 1));
            return null;
        });

        assertEquals(8, stock(FILTRO));
        assertEquals(1, linea(SERVICIO_A, FILTRO)[0]);
        assertEquals(1, linea(SERVICIO_A, FILTRO)[1]);
        assertEquals(1, entero("SELECT COUNT(*) FROM servicios WHERE costo_mano_obra = 50000"));
    }

    @Test
    void conUnaConexionEnAutocommitNoOperaNiCambiaNada() throws SQLException {
        try (Connection c = esquema.conectar()) {
            c.setAutoCommit(true);
            InventarioServiciosJdbc inventario = new InventarioServiciosJdbc(c);

            assertThrows(IllegalStateException.class, () -> inventario.reservar(new ReservaRepuesto(SERVICIO_A, FILTRO, 1)));
        }
        assertEquals(STOCK_INICIAL, stock(FILTRO));
    }

    // ---------- conexiones ----------

    /** Conexiones abiertas en MySQL por el usuario de pruebas, sin contar la que consulta. */
    private int conexionesAbiertas() throws SQLException {
        return entero("SELECT COUNT(*) - 1 FROM information_schema.PROCESSLIST "
                + "WHERE USER = SUBSTRING_INDEX(CURRENT_USER(), '@', 1)");
    }

    @Test
    void noQuedanConexionesAbiertasTrasOperacionesCorrectasYFallidas() throws Exception {
        int antes = conexionesAbiertas();
        RepuestoDAO repuestosDao = new RepuestoDAO(esquema::conectar);

        for (int i = 0; i < 5; i++) {
            reservar(SERVICIO_A, FILTRO, 1);
            consumir(SERVICIO_A, FILTRO, 1);
            assertThrows(StockInsuficienteException.class, () -> reservar(SERVICIO_B, BUJIA, 99));
            assertThrows(IllegalStateException.class, () -> consumir(SERVICIO_A, FILTRO, 50));
            assertThrows(NoSuchElementException.class, () -> liberar(SERVICIO_B, FILTRO, 1));
            assertThrows(IllegalArgumentException.class, () -> transaccion((c, inv) -> {
                inv.reservar(new ReservaRepuesto(SERVICIO_B, BUJIA, 1));
                throw new IllegalArgumentException("Regla de Servicios incumplida");
            }));
            repuestosDao.registrarEntrada(BUJIA, 1);
            assertThrows(StockInsuficienteException.class, () -> repuestosDao.registrarSalida(BUJIA, 999));
        }

        // El servidor libera el hilo de una conexión cerrada casi de inmediato; se espera hasta 3 s.
        int despues = conexionesAbiertas();
        for (int intento = 0; intento < 30 && despues > antes; intento++) {
            Thread.sleep(100);
            despues = conexionesAbiertas();
        }
        assertEquals(antes, despues, "Cada operación debe cerrar su conexión, también cuando falla");
        assertEquals(STOCK_INICIAL, totalFisico(FILTRO));
    }

    // ---------- concurrencia ----------

    private static <T> List<Future<T>> enParalelo(int hilos, Callable<T> tarea) throws InterruptedException {
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(ejecutor.submit(() -> {
                salida.await();
                return tarea.call();
            }));
        }
        salida.countDown();
        ejecutor.shutdown();
        assertTrue(ejecutor.awaitTermination(90, TimeUnit.SECONDS), "Las operaciones concurrentes no terminaron");
        return resultados;
    }

    @Test
    void reservasConcurrentesNuncaComprometenMasUnidadesDeLasDisponibles() throws Exception {
        AtomicInteger turno = new AtomicInteger();

        List<Future<Boolean>> resultados = enParalelo(20, () -> {
            int servicio = turno.getAndIncrement() % 2 == 0 ? SERVICIO_A : SERVICIO_B;
            try {
                reservar(servicio, FILTRO, 1);
                return true;
            } catch (StockInsuficienteException e) {
                return false;
            }
        });

        int aceptadas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) {
                aceptadas++;
            }
        }
        assertEquals(STOCK_INICIAL, aceptadas);
        assertEquals(0, stock(FILTRO));
        assertEquals(STOCK_INICIAL, linea(SERVICIO_A, FILTRO)[0] + linea(SERVICIO_B, FILTRO)[0]);
    }

    @Test
    void operacionesConcurrentesMixtasConservanLasUnidadesFisicas() throws Exception {
        reservar(SERVICIO_A, FILTRO, 4);
        reservar(SERVICIO_B, FILTRO, 4);
        AtomicInteger errores = new AtomicInteger();

        List<Future<Void>> resultados = enParalelo(40, () -> {
            int servicio = ThreadLocalRandom.current().nextBoolean() ? SERVICIO_A : SERVICIO_B;
            try {
                switch (ThreadLocalRandom.current().nextInt(4)) {
                    case 0 -> reservar(servicio, FILTRO, 1);
                    case 1 -> consumir(servicio, FILTRO, 1);
                    case 2 -> liberar(servicio, FILTRO, 1);
                    default -> transaccion((c, inv) -> inv.liberarTodo(servicio));
                }
            } catch (StockInsuficienteException | NoSuchElementException e) {
                // Rechazos de negocio válidos.
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("reserva pendiente")) {
                    errores.incrementAndGet();
                    throw e;
                }
            }
            return null;
        });
        for (Future<Void> r : resultados) {
            r.get();
        }

        assertEquals(0, errores.get(), "No debía haber errores JDBC (por ejemplo, interbloqueos)");
        assertEquals(STOCK_INICIAL, totalFisico(FILTRO), "bodega + reservado + usado = unidades iniciales");
        assertTrue(stock(FILTRO) >= 0);
        assertTrue(linea(SERVICIO_A, FILTRO)[0] >= 0 && linea(SERVICIO_B, FILTRO)[0] >= 0);
    }

    @Test
    void transaccionesQueConsumenYReservanNoSeInterbloqueanConReservasSimultaneas() throws Exception {
        reservar(SERVICIO_A, FILTRO, 5);
        AtomicInteger turno = new AtomicInteger();
        AtomicInteger errores = new AtomicInteger();

        List<Future<Void>> resultados = enParalelo(30, () -> {
            boolean combinada = turno.getAndIncrement() % 2 == 0;
            try {
                if (combinada) {
                    // Una sola transacción de Servicios: consume una unidad y repone la reserva.
                    transaccion((c, inv) -> {
                        inv.consumir(new ConsumoRepuesto(SERVICIO_A, FILTRO, 1));
                        inv.reservar(new ReservaRepuesto(SERVICIO_A, FILTRO, 1));
                        return null;
                    });
                } else {
                    reservar(SERVICIO_A, FILTRO, 1);
                }
            } catch (StockInsuficienteException e) {
                // Rechazo de negocio válido: la transacción completa se deshizo.
            } catch (IllegalStateException e) {
                errores.incrementAndGet();
                throw e;
            }
            return null;
        });
        for (Future<Void> r : resultados) {
            r.get();
        }

        assertEquals(0, errores.get(), "No debía haber interbloqueos ni otros errores JDBC");
        assertEquals(STOCK_INICIAL, totalFisico(FILTRO), "bodega + reservado + usado = unidades iniciales");
        assertTrue(stock(FILTRO) >= 0);
    }
}
