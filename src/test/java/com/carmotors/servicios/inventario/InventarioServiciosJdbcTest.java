package com.carmotors.servicios.inventario;

import com.carmotors.dao.FuenteConexion;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de transacción del adaptador de Inventario para Servicios, sin MySQL. */
class InventarioServiciosJdbcTest {

    /** Conexión simulada: registra eventos y hace fallar cualquier sentencia SQL. */
    private static final class Conexion {
        final List<String> eventos = new ArrayList<>();
        boolean autoCommit;

        Conexion(boolean autoCommit) {
            this.autoCommit = autoCommit;
        }

        Connection proxy() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, metodo, args) -> switch (metodo.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            autoCommit = (Boolean) args[0];
                            eventos.add("setAutoCommit(" + autoCommit + ")");
                            yield null;
                        }
                        case "commit", "rollback", "close" -> {
                            eventos.add(metodo.getName());
                            yield null;
                        }
                        case "prepareStatement" -> {
                            eventos.add("sql");
                            throw new SQLException("Fallo JDBC simulado");
                        }
                        default -> throw new UnsupportedOperationException(metodo.getName());
                    });
        }

        FuenteConexion fuente() {
            return this::proxy;
        }
    }

    @Test
    void exigeUnaConexion() {
        assertThrows(NullPointerException.class, () -> new InventarioServiciosJdbc(null));
    }

    @Test
    void rechazaOperarSinTransaccionYNoEjecutaSql() {
        Conexion conexion = new Conexion(true);
        InventarioServiciosJdbc inventario = new InventarioServiciosJdbc(conexion.proxy());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> inventario.reservar(new ReservaRepuesto(1, 1, 1)));

        assertTrue(error.getMessage().contains("autoCommit"));
        assertThrows(IllegalStateException.class, () -> inventario.consumir(new ConsumoRepuesto(1, 1, 1)));
        assertThrows(IllegalStateException.class, () -> inventario.liberar(new ReservaRepuesto(1, 1, 1)));
        assertFalse(conexion.eventos.contains("sql"), "No debía ejecutar SQL fuera de una transacción");
    }

    @Test
    void unErrorJdbcLlegaComoIllegalStateExceptionConLaCausaYSinConfirmarNada() {
        Conexion conexion = new Conexion(false);
        InventarioServiciosJdbc inventario = new InventarioServiciosJdbc(conexion.proxy());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> inventario.reservar(new ReservaRepuesto(4, 7, 2)));

        assertInstanceOf(SQLException.class, error.getCause());
        assertTrue(error.getMessage().contains("rollback"));
        assertFalse(conexion.eventos.contains("commit"), "El adaptador nunca confirma por su cuenta");
        assertFalse(conexion.eventos.contains("rollback"), "El rollback lo decide quien coordina");
        assertFalse(conexion.eventos.contains("close"), "No debe cerrar la conexión de Servicios");
    }

    @Test
    void validaElServicioAlCancelar() {
        InventarioServiciosJdbc inventario = new InventarioServiciosJdbc(new Conexion(false).proxy());

        assertThrows(IllegalArgumentException.class, () -> inventario.liberarTodo(0));
        assertThrows(NullPointerException.class, () -> inventario.reservar(null));
    }

    @Test
    void ejecutarEnTransaccionConfirmaSiTodoSaleBien() throws SQLException {
        Conexion conexion = new Conexion(true);

        String resultado = InventarioServiciosJdbc.ejecutarEnTransaccion(conexion.fuente(), (c, inv) -> "ok");

        assertEquals("ok", resultado);
        assertEquals(List.of("setAutoCommit(false)", "commit", "setAutoCommit(true)", "close"), conexion.eventos);
    }

    @Test
    void ejecutarEnTransaccionHaceRollbackCompletoAnteCualquierError() {
        Conexion conexion = new Conexion(true);

        assertThrows(IllegalStateException.class, () -> InventarioServiciosJdbc.ejecutarEnTransaccion(
                conexion.fuente(), (c, inventario) -> {
                    inventario.reservar(new ReservaRepuesto(1, 1, 1));
                    return null;
                }));

        assertEquals(List.of("setAutoCommit(false)", "sql", "rollback", "setAutoCommit(true)", "close"),
                conexion.eventos);
    }

    @Test
    void ejecutarEnTransaccionTambienDeshaceErroresDeNegocio() {
        Conexion conexion = new Conexion(true);

        assertThrows(IllegalArgumentException.class, () -> InventarioServiciosJdbc.ejecutarEnTransaccion(
                conexion.fuente(), (c, inventario) -> {
                    throw new IllegalArgumentException("Regla de Servicios incumplida");
                }));

        assertTrue(conexion.eventos.contains("rollback"));
        assertFalse(conexion.eventos.contains("commit"));
    }
}
