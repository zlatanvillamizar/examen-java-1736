package com.carmotors.dao;

import com.carmotors.modelo.Factura;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacturaTransaccionTest {
    @Test
    void conservaFalloDeRestauracionTrasRollback() {
        verificarFallo(false);
    }

    @Test
    void conservaFalloDeRollbackYEvitaCommitImplicito() {
        verificarFallo(true);
    }

    private void verificarFallo(boolean fallaRollback) {
        var operaciones = new ArrayList<String>();
        var falloInsercion = new SQLException("insert");
        var falloSecundario = new SQLException(fallaRollback ? "rollback" : "autoCommit");
        var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, metodo, args) -> {
                    if (metodo.getName().equals("executeUpdate")) {
                        operaciones.add("insert");
                        throw falloInsercion;
                    }
                    if (metodo.getName().equals("close")) operaciones.add("cerrar sentencia");
                    return null;
                });
        var conexion = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "getAutoCommit":
                            return true;
                        case "setAutoCommit":
                            operaciones.add("autoCommit=" + args[0]);
                            if (Boolean.TRUE.equals(args[0])) throw falloSecundario;
                            return null;
                        case "prepareStatement":
                            return statement;
                        case "rollback":
                            operaciones.add("rollback");
                            if (fallaRollback) throw falloSecundario;
                            return null;
                        default:
                            throw new AssertionError(metodo.getName());
                    }
                });

        Factura factura = new Factura();
        factura.setFechaEmision(LocalDateTime.now());
        SQLException error = assertThrows(SQLException.class,
                () -> new FacturaDAO().insertarEnTransaccion(conexion, factura));
        assertSame(falloInsercion, error);
        assertArrayEquals(new Throwable[]{falloSecundario}, error.getSuppressed());
        List<String> esperado = new ArrayList<>(
                List.of("autoCommit=false", "insert", "cerrar sentencia", "rollback"));
        if (!fallaRollback) esperado.add("autoCommit=true");
        assertEquals(esperado, operaciones);
        assertEquals(0, factura.getId());
    }
}
