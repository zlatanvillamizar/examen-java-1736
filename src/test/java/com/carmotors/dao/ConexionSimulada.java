package com.carmotors.dao;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Conexión JDBC simulada para probar el manejo de transacciones de los DAO sin MySQL.
 *
 * <p>Registra las llamadas a {@code commit}, {@code rollback}, {@code setAutoCommit}
 * y {@code close}. Cada {@code prepareStatement(sql)} se resuelve con la función
 * {@code respuestas}, que decide para ese SQL cuántas filas actualiza un
 * {@code executeUpdate}, qué valor devuelve una consulta o si debe fallar.</p>
 */
final class ConexionSimulada {

    /** Comportamiento de una sentencia simulada. */
    record Respuesta(int filasActualizadas, Integer valorConsulta, SQLException error) {
        static Respuesta actualiza(int filas) {
            return new Respuesta(filas, null, null);
        }

        static Respuesta consulta(Integer valor) {
            return new Respuesta(0, valor, null);
        }

        static Respuesta falla(String mensaje) {
            return new Respuesta(0, null, new SQLException(mensaje));
        }
    }

    final List<String> eventos = new ArrayList<>();
    boolean autoCommit = true;
    SQLException errorEnCommit;
    private final Function<String, Respuesta> respuestas;

    ConexionSimulada(Function<String, Respuesta> respuestas) {
        this.respuestas = respuestas;
    }

    FuenteConexion fuente() {
        return this::conexion;
    }

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> {
                        autoCommit = (Boolean) args[0];
                        eventos.add("setAutoCommit(" + autoCommit + ")");
                        yield null;
                    }
                    case "commit" -> {
                        if (errorEnCommit != null) {
                            eventos.add("commit-fallido");
                            throw errorEnCommit;
                        }
                        eventos.add("commit");
                        yield null;
                    }
                    case "rollback" -> {
                        eventos.add("rollback");
                        yield null;
                    }
                    case "close" -> {
                        eventos.add("close");
                        yield null;
                    }
                    case "prepareStatement" -> sentencia((String) args[0]);
                    case "isClosed" -> false;
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private PreparedStatement sentencia(String sql) {
        Respuesta respuesta = respuestas.apply(sql);
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "setInt", "setString", "setBigDecimal", "close" -> null;
                    case "executeUpdate" -> {
                        eventos.add("update: " + sql.split(" ")[0]);
                        if (respuesta.error() != null) {
                            throw respuesta.error();
                        }
                        yield respuesta.filasActualizadas();
                    }
                    case "executeQuery" -> {
                        eventos.add("query");
                        if (respuesta.error() != null) {
                            throw respuesta.error();
                        }
                        yield resultado(respuesta.valorConsulta());
                    }
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }

    private ResultSet resultado(Integer valor) {
        boolean[] leido = {false};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "next" -> {
                        boolean hay = valor != null && !leido[0];
                        leido[0] = true;
                        yield hay;
                    }
                    case "getInt" -> valor;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }
}
