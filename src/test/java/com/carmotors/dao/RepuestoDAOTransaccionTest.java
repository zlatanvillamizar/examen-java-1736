package com.carmotors.dao;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manejo de transacciones de {@link RepuestoDAO} ante errores JDBC, sin MySQL:
 * se usa una {@link ConexionSimulada} que registra commit, rollback, autocommit y cierre.
 */
class RepuestoDAOTransaccionTest {

    private static boolean esUpdate(String sql) {
        return sql.startsWith("UPDATE");
    }

    @Test
    void unaSalidaCorrectaHaceCommitYRestauraElAutocommit() throws SQLException {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.actualiza(1)
                : ConexionSimulada.Respuesta.consulta(7));

        assertEquals(7, new RepuestoDAO(conexion.fuente()).registrarSalida(1, 3));

        assertEquals(List.of("setAutoCommit(false)", "update: UPDATE", "query", "commit",
                "setAutoCommit(true)", "close"), conexion.eventos);
        assertTrue(conexion.autoCommit);
    }

    @Test
    void unErrorJdbcEnElUpdateHaceRollbackSinCommit() {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.falla("Fallo simulado en UPDATE")
                : ConexionSimulada.Respuesta.consulta(7));

        SQLException error = assertThrows(SQLException.class,
                () -> new RepuestoDAO(conexion.fuente()).registrarSalida(1, 3));

        assertEquals("Fallo simulado en UPDATE", error.getMessage());
        assertTrue(conexion.eventos.contains("rollback"));
        assertFalse(conexion.eventos.contains("commit"));
        assertTrue(conexion.autoCommit, "Debe restaurar el autocommit");
        assertEquals("close", conexion.eventos.get(conexion.eventos.size() - 1));
    }

    @Test
    void unErrorAlLeerElStockDespuesDelUpdateDeshaceElUpdate() {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.actualiza(1)
                : ConexionSimulada.Respuesta.falla("Fallo simulado en SELECT"));

        assertThrows(SQLException.class, () -> new RepuestoDAO(conexion.fuente()).registrarEntrada(1, 5));

        assertEquals(List.of("setAutoCommit(false)", "update: UPDATE", "query", "rollback",
                "setAutoCommit(true)", "close"), conexion.eventos);
    }

    @Test
    void unFalloAlConfirmarHaceRollback() {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.actualiza(1)
                : ConexionSimulada.Respuesta.consulta(12));
        conexion.errorEnCommit = new SQLException("Fallo simulado en COMMIT");

        assertThrows(SQLException.class, () -> new RepuestoDAO(conexion.fuente()).registrarEntrada(1, 2));

        int commit = conexion.eventos.indexOf("commit-fallido");
        assertTrue(commit >= 0);
        assertEquals("rollback", conexion.eventos.get(commit + 1));
        assertTrue(conexion.autoCommit);
    }

    @Test
    void elStockInsuficienteHaceRollbackSinCommit() {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.actualiza(0)
                : ConexionSimulada.Respuesta.consulta(2));

        StockInsuficienteException error = assertThrows(StockInsuficienteException.class,
                () -> new RepuestoDAO(conexion.fuente()).registrarSalida(1, 5));

        assertEquals(2, error.getDisponible());
        assertTrue(conexion.eventos.contains("rollback"));
        assertFalse(conexion.eventos.contains("commit"));
    }

    @Test
    void unRepuestoInexistenteHaceRollbackSinCommit() {
        ConexionSimulada conexion = new ConexionSimulada(sql -> esUpdate(sql)
                ? ConexionSimulada.Respuesta.actualiza(0)
                : ConexionSimulada.Respuesta.consulta(null));

        assertThrows(RepuestoNoEncontradoException.class,
                () -> new RepuestoDAO(conexion.fuente()).registrarSalida(99, 1));
        assertThrows(RepuestoNoEncontradoException.class,
                () -> new RepuestoDAO(conexion.fuente()).registrarEntrada(99, 1));

        assertFalse(conexion.eventos.contains("commit"));
        assertEquals(2, conexion.eventos.stream().filter("rollback"::equals).count());
    }
}
