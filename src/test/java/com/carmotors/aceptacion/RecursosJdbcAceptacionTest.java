package com.carmotors.aceptacion;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.servicios.inventario.InventarioService;
import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class RecursosJdbcAceptacionTest {
    @Test
    void inventarioYProveedoresCierranRecursosCuandoElIdNoExiste() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado(); var conexiones = new Conexiones(esquema)) {
            InventarioControlador inventario = conexiones.controlador();
            assertTrue(inventario.buscarRepuesto(999).isEmpty());
            assertTrue(inventario.buscarProveedor(999).isEmpty());
            conexiones.verificarCierre(2);
        }
    }

    @Test
    void unErrorDeIntegridadCierraRecursosYPermiteLaSiguienteOperacion() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado(); var conexiones = new Conexiones(esquema)) {
            InventarioControlador inventario = conexiones.controlador();
            var error = assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> inventario.registrarRepuesto("Filtro", "Bosch", 999, BigDecimal.TEN, 2, 0));
            assertEquals("23000", error.getSQLState());
            conexiones.verificarCierre(1);
            assertTrue(inventario.listarRepuestos().isEmpty());
            conexiones.verificarCierre(2);
        }
    }

    private static final class Conexiones implements AutoCloseable {
        private final EsquemaInventarioPrueba esquema;
        private final List<Connection> abiertas = new ArrayList<>();

        Conexiones(EsquemaInventarioPrueba esquema) {
            this.esquema = esquema;
        }

        InventarioControlador controlador() {
            return new InventarioControlador(new InventarioService(
                    new RepuestoDAO(this::conectar), new ProveedorDAO(this::conectar)));
        }

        private Connection conectar() throws SQLException {
            Connection conexion = esquema.conectar();
            abiertas.add(conexion);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, metodo, argumentos) -> {
                        if (metodo.getName().equals("close")) {
                            assertEquals(0, conexion.unwrap(JdbcConnection.class).getActiveStatementCount(),
                                    "Las sentencias deben cerrarse antes de la conexión");
                        }
                        try {
                            return metodo.invoke(conexion, argumentos);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        void verificarCierre(int total) throws SQLException {
            assertEquals(total, abiertas.size());
            for (Connection conexion : abiertas) {
                assertTrue(conexion.isClosed(), "La operación dejó abierta una conexión");
            }
        }

        @Override
        public void close() throws SQLException {
            for (Connection conexion : abiertas) {
                conexion.close();
            }
        }
    }
}
