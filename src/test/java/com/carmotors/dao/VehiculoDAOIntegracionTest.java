package com.carmotors.dao;

import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.vehiculos.VehiculoService;
import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaVehiculosPrueba.VARIABLE_URL, matches = ".+")
class VehiculoDAOIntegracionTest {
    private EsquemaVehiculosPrueba esquema;
    private VehiculoDAO dao;
    private VehiculoControlador controlador;
    private final List<Connection> conexiones = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        esquema = EsquemaVehiculosPrueba.crear();
        esquema.ejecutar("INSERT INTO clientes (id, nombre, documento_identidad, email) "
                + "VALUES (1, 'Cliente de prueba', 'TEST-1', 'vehiculos@example.test')");
        dao = new VehiculoDAO(this::conexionObservada);
        controlador = new VehiculoControlador(new VehiculoService(dao, new ClienteDAO(esquema::conectar)));
    }

    @AfterEach
    void eliminarEsquema() throws SQLException {
        if (esquema != null) {
            try (EsquemaVehiculosPrueba base = esquema) {
                for (Connection conexion : conexiones) {
                    try (conexion) {
                        assertTrue(conexion.isClosed(),
                                "El DAO debe cerrar la conexión incluso si falla la operación");
                    }
                }
            }
        }
    }

    @Test
    void registraConsultaYListaEntreConexiones() throws SQLException {
        assertTrue(controlador.listarVehiculos().isEmpty());
        Vehiculo primero = controlador.registrarVehiculo(vehiculo(1, " abc123 "));
        Vehiculo segundo = controlador.registrarVehiculo(vehiculo(1, "DEF456"));

        assertTrue(primero.getId() > 0);
        assertNotSame(conexiones.get(0), conexiones.get(1));
        Vehiculo encontrado = controlador.buscarVehiculo(primero.getId()).orElseThrow();
        assertAll(() -> assertEquals(primero.getId(), encontrado.getId()),
                () -> assertEquals(1, encontrado.getClienteId()),
                () -> assertEquals("ABC123", encontrado.getPlaca()),
                () -> assertEquals("Mazda", encontrado.getMarca()),
                () -> assertEquals("3", encontrado.getModelo()),
                () -> assertEquals(2020, encontrado.getAnio()));
        assertEquals(primero.getId(), controlador.buscarPorPlaca(" abc123 ").orElseThrow().getId());
        assertEquals(List.of(primero.getId(), segundo.getId()),
                controlador.listarVehiculos().stream().map(Vehiculo::getId).toList());
        assertTrue(controlador.buscarVehiculo(segundo.getId() + 1).isEmpty());
        assertTrue(controlador.buscarPorPlaca("ZZZ999").isEmpty());
    }

    @Test
    void rechazaPlacaDuplicadaEnServicioYEnMySql() throws SQLException {
        controlador.registrarVehiculo(vehiculo(1, "ABC123"));
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(vehiculo(1, "abc123")));
        var error = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> dao.registrar(vehiculo(1, "ABC123")));
        assertEquals(1062, error.getErrorCode());
        assertEquals(1, dao.listar().size());
    }

    @Test
    void rechazaClienteInexistenteYProtegeLaClaveForanea() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(vehiculo(999, "ABC123")));
        var insercion = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> dao.registrar(vehiculo(999, "ABC123")));
        assertEquals(1452, insercion.getErrorCode());

        controlador.registrarVehiculo(vehiculo(1, "ABC123"));
        var borrado = assertThrows(SQLIntegrityConstraintViolationException.class,
                () -> esquema.ejecutar("DELETE FROM clientes WHERE id = 1"));
        assertEquals(1451, borrado.getErrorCode());
        assertEquals(1, dao.listar().size());
    }

    @Test
    void propagaRechazoRealDeConexion() {
        String usuarioInexistente = UUID.randomUUID().toString().replace("-", "");
        VehiculoDAO sinAcceso = new VehiculoDAO(() -> DriverManager.getConnection(
                System.getenv(EsquemaVehiculosPrueba.VARIABLE_URL), usuarioInexistente, ""));
        SQLException error = assertThrows(SQLException.class, sinAcceso::listar);
        assertEquals("28000", error.getSQLState());
    }

    private Connection conexionObservada() throws SQLException {
        Connection conexion = esquema.conectar();
        conexiones.add(conexion);
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, argumentos) -> {
                    if (metodo.getName().equals("close")) {
                        try {
                            assertEquals(0, conexion.unwrap(JdbcConnection.class).getActiveStatementCount(),
                                    "El DAO debe cerrar sus sentencias antes de cerrar la conexión");
                        } finally {
                            conexion.close();
                        }
                        return null;
                    }
                    try {
                        return metodo.invoke(conexion, argumentos);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private Vehiculo vehiculo(int clienteId, String placa) {
        return new Vehiculo(clienteId, placa, "Mazda", "3", 2020);
    }
}
