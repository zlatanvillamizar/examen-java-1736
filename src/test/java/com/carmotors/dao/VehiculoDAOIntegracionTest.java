package com.carmotors.dao;

import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.vehiculos.VehiculoService;
import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class VehiculoDAOIntegracionTest {
    private EsquemaInventarioPrueba esquema;
    private VehiculoDAO dao;
    private VehiculoControlador controlador;
    private final List<Connection> conexiones = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        Path bloques = Path.of("database", "bloques");
        String clientes = EsquemaInventarioPrueba.instrucciones(Files.readString(bloques.resolve("clientes.sql")))
                .stream().filter(sql -> sql.startsWith("CREATE TABLE clientes ("))
                .findFirst().orElseThrow();
        List<String> tablas = new ArrayList<>(List.of(clientes));
        tablas.addAll(EsquemaInventarioPrueba.instrucciones(Files.readString(bloques.resolve("vehiculos.sql"))));
        esquema = EsquemaInventarioPrueba.crearConTablas(tablas);
        esquema.ejecutar("INSERT INTO clientes (id, nombre, documento_identidad, email) "
                + "VALUES (1, 'Cliente de prueba', 'TEST-1', 'vehiculos@example.test')");
        dao = new VehiculoDAO(this::conexionObservada);
        // El DAO compartido de Clientes usa la conexión fija de la aplicación.
        ClienteDAO clientesDePrueba = new ClienteDAO() {
            @Override
            public Optional<Cliente> buscarPorId(int id) throws SQLException {
                try (var conexion = esquema.conectar();
                     var consulta = conexion.prepareStatement("SELECT id FROM clientes WHERE id = ?")) {
                    consulta.setInt(1, id);
                    try (var filas = consulta.executeQuery()) {
                        if (!filas.next()) {
                            return Optional.empty();
                        }
                        Cliente cliente = new Cliente();
                        cliente.setId(filas.getInt("id"));
                        return Optional.of(cliente);
                    }
                }
            }
        };
        controlador = new VehiculoControlador(new VehiculoService(dao, clientesDePrueba));
    }

    @AfterEach
    void eliminarEsquema() throws SQLException {
        if (esquema != null) {
            try (EsquemaInventarioPrueba base = esquema) {
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
                System.getenv(EsquemaInventarioPrueba.VARIABLE_URL), usuarioInexistente, ""));
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
