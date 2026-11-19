package com.carmotors.dao;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manejo de transacción compartido por {@code RepuestoDAO} y el adaptador de Servicios. */
class TransaccionTest {

    /** Conexión simulada que registra eventos y puede fallar en commit, rollback o setAutoCommit. */
    private static final class Conexion {
        final List<String> eventos = new ArrayList<>();
        boolean autoCommit = true;
        boolean fallaCommit;
        boolean fallaRollback;
        boolean fallaRestaurar;

        FuenteConexion fuente() {
            return () -> (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, metodo, args) -> switch (metodo.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            boolean valor = (Boolean) args[0];
                            if (valor && fallaRestaurar) {
                                eventos.add("setAutoCommit-fallido");
                                throw new SQLException("Fallo al restaurar autoCommit");
                            }
                            autoCommit = valor;
                            eventos.add("setAutoCommit(" + valor + ")");
                            yield null;
                        }
                        case "commit" -> {
                            if (fallaCommit) {
                                eventos.add("commit-fallido");
                                throw new SQLException("Fallo en commit");
                            }
                            eventos.add("commit");
                            yield null;
                        }
                        case "rollback" -> {
                            if (fallaRollback) {
                                eventos.add("rollback-fallido");
                                throw new SQLException("Fallo en rollback");
                            }
                            eventos.add("rollback");
                            yield null;
                        }
                        case "close" -> {
                            eventos.add("close");
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(metodo.getName());
                    });
        }
    }

    @Test
    void confirmaRestauraYCierraSiTodoSaleBien() throws SQLException {
        Conexion conexion = new Conexion();

        int resultado = Transaccion.ejecutar(conexion.fuente(), c -> 7);

        assertEquals(7, resultado);

        assertEquals(List.of("setAutoCommit(false)", "commit", "setAutoCommit(true)", "close"), conexion.eventos);
    }

    @Test
    void conservaLaExcepcionOriginalSiTambienFallanRollbackYRestauracion() {
        Conexion conexion = new Conexion();
        conexion.fallaRollback = true;
        conexion.fallaRestaurar = true;
        StockInsuficienteException original = new StockInsuficienteException(1, 5, 2);

        StockInsuficienteException lanzada = assertThrows(StockInsuficienteException.class,
                () -> Transaccion.ejecutar(conexion.fuente(), c -> {
                    throw original;
                }));

        assertSame(original, lanzada);
        List<String> suprimidas = Arrays.stream(lanzada.getSuppressed()).map(Throwable::getMessage).toList();
        assertEquals(List.of("Fallo en rollback", "Fallo al restaurar autoCommit"), suprimidas);
        assertEquals(List.of("setAutoCommit(false)", "rollback-fallido", "setAutoCommit-fallido", "close"),
                conexion.eventos);
    }

    @Test
    void unFalloEnCommitHaceRollbackYSePropaga() {
        Conexion conexion = new Conexion();
        conexion.fallaCommit = true;

        SQLException error = assertThrows(SQLException.class, () -> Transaccion.ejecutar(conexion.fuente(), c -> 1));

        assertEquals("Fallo en commit", error.getMessage());
        assertEquals(List.of("setAutoCommit(false)", "commit-fallido", "rollback", "setAutoCommit(true)", "close"),
                conexion.eventos);
    }

    @Test
    void tambienDeshaceAnteUnError() {
        Conexion conexion = new Conexion();

        assertThrows(AssertionError.class, () -> Transaccion.ejecutar(conexion.fuente(), c -> {
            throw new AssertionError("Error grave simulado");
        }));

        assertTrue(conexion.eventos.contains("rollback"));
        assertTrue(conexion.eventos.contains("close"));
    }

    @Test
    void unFalloAlRestaurarTrasUnCommitCorrectoNoSeReportaComoFallo() throws SQLException {
        Conexion conexion = new Conexion();
        conexion.fallaRestaurar = true;

        assertEquals("ok", Transaccion.ejecutar(conexion.fuente(), c -> "ok"));

        assertEquals(List.of("setAutoCommit(false)", "commit", "setAutoCommit-fallido", "close"), conexion.eventos);
    }

    @Test
    void exigeFuenteYTrabajo() {
        assertThrows(NullPointerException.class, () -> Transaccion.ejecutar(null, c -> 1));
        assertThrows(NullPointerException.class, () -> Transaccion.ejecutar(new Conexion().fuente(), null));
    }
}
