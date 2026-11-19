package com.carmotors.dao;

import com.carmotors.modelo.Repuesto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atomicidad y concurrencia de los movimientos de stock de {@link RepuestoDAO} contra
 * MySQL real. Usa una base desechable propia (ver {@link EsquemaInventarioPrueba}) y
 * solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class RepuestoDAOTransaccionIntegracionTest {

    private EsquemaInventarioPrueba esquema;
    private RepuestoDAO dao;

    @BeforeEach
    void crearBaseDesechable() throws SQLException, IOException {
        esquema = EsquemaInventarioPrueba.crear();
        esquema.ejecutar("INSERT INTO repuesto_tipos (nombre) VALUES ('Filtros')");
        dao = new RepuestoDAO(esquema::conectar);
    }

    @AfterEach
    void eliminarBaseDesechable() throws SQLException {
        if (esquema != null) {
            esquema.close();
        }
    }

    private Repuesto conStock(int stock) throws SQLException {
        return dao.registrar(Repuesto.nuevo("Filtro de aceite", "Bosch", 1, new BigDecimal("25000.00"), stock, 2));
    }

    private int stockEnBase(int id) throws SQLException {
        return dao.buscarPorId(id).orElseThrow().cantidadStock();
    }

    /**
     * Conexión real que falla a propósito: al preparar una sentencia que empieza
     * con {@code prefijoQueFalla}, o al confirmar si {@code fallarEnCommit} es true.
     */
    private FuenteConexion conFallo(String prefijoQueFalla, boolean fallarEnCommit) {
        return () -> {
            Connection real = esquema.conectar();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                        if (prefijoQueFalla != null && metodo.getName().equals("prepareStatement")
                                && ((String) args[0]).startsWith(prefijoQueFalla)) {
                            throw new SQLException("Fallo simulado en: " + prefijoQueFalla);
                        }
                        if (fallarEnCommit && metodo.getName().equals("commit")) {
                            throw new SQLException("Fallo simulado en COMMIT");
                        }
                        try {
                            return metodo.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        };
    }

    // ---------- atomicidad ante errores JDBC ----------

    @Test
    void siFallaLaLecturaDespuesDeLaSalidaElUpdateSeDeshaceEnLaBase() throws SQLException {
        Repuesto repuesto = conStock(10);
        RepuestoDAO conError = new RepuestoDAO(conFallo("SELECT cantidad_stock", false));

        assertThrows(SQLException.class, () -> conError.registrarSalida(repuesto.id(), 4));

        assertEquals(10, stockEnBase(repuesto.id()), "El UPDATE ya ejecutado debía deshacerse");
    }

    @Test
    void siFallaElCommitDeUnaEntradaElStockNoCambia() throws SQLException {
        Repuesto repuesto = conStock(10);
        RepuestoDAO conError = new RepuestoDAO(conFallo(null, true));

        assertThrows(SQLException.class, () -> conError.registrarEntrada(repuesto.id(), 5));

        assertEquals(10, stockEnBase(repuesto.id()));
    }

    @Test
    void unaSalidaRechazadaPorStockInsuficienteNoDejaCambios() throws SQLException {
        Repuesto repuesto = conStock(3);

        assertThrows(StockInsuficienteException.class, () -> dao.registrarSalida(repuesto.id(), 4));

        assertEquals(3, stockEnBase(repuesto.id()));
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
        assertTrue(ejecutor.awaitTermination(60, TimeUnit.SECONDS), "Las operaciones concurrentes no terminaron");
        return resultados;
    }

    @Test
    void salidasConcurrentesNuncaDejanElStockNegativoNiDescuentanDeMas() throws Exception {
        Repuesto repuesto = conStock(10);
        AtomicInteger rechazadas = new AtomicInteger();

        List<Future<Boolean>> resultados = enParalelo(20, () -> {
            try {
                dao.registrarSalida(repuesto.id(), 1);
                return true;
            } catch (StockInsuficienteException e) {
                rechazadas.incrementAndGet();
                return false;
            }
        });

        int aceptadas = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get()) {
                aceptadas++;
            }
        }
        assertEquals(10, aceptadas, "Solo debían aceptarse tantas salidas como unidades había");
        assertEquals(10, rechazadas.get());
        assertEquals(0, stockEnBase(repuesto.id()));
    }

    @Test
    void entradasYSalidasConcurrentesConservanElBalance() throws Exception {
        Repuesto repuesto = conStock(5);
        AtomicInteger entradas = new AtomicInteger();
        AtomicInteger salidas = new AtomicInteger();
        AtomicInteger contador = new AtomicInteger();

        List<Future<Void>> resultados = enParalelo(30, () -> {
            if (contador.getAndIncrement() % 2 == 0) {
                dao.registrarEntrada(repuesto.id(), 2);
                entradas.addAndGet(2);
            } else {
                try {
                    dao.registrarSalida(repuesto.id(), 3);
                    salidas.addAndGet(3);
                } catch (StockInsuficienteException e) {
                    // Rechazo válido: no cambia el stock.
                }
            }
            return null;
        });
        for (Future<Void> resultado : resultados) {
            resultado.get();
        }

        int esperado = 5 + entradas.get() - salidas.get();
        assertEquals(esperado, stockEnBase(repuesto.id()), "stock = inicial + entradas − salidas aceptadas");
        assertTrue(stockEnBase(repuesto.id()) >= 0);
    }
}
