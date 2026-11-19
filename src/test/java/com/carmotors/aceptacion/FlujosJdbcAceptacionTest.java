package com.carmotors.aceptacion;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.controlador.InventarioControlador;
import com.carmotors.controlador.ServicioControlador;
import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.FuenteConexion;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.InventarioServiciosPort;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Isolated
@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class FlujosJdbcAceptacionTest {
    private static final String ADAPTADOR = "com.carmotors.servicios.inventario.InventarioServiciosJdbc";
    private static final Class<?>[] SIN_ARGUMENTOS = {};

    @Test
    void registraClienteYVehiculoYCompruebaLaRelacionPersistida() throws Exception {
        try (var esquema = crearEsquema();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            var cliente = new ClienteControlador().registrar("Cliente QA", "QA-1", null, "qa@example.test");
            var registrado = new VehiculoControlador().registrarVehiculo(
                    new Vehiculo(cliente.getId(), "qa1234", "Mazda", "3", 2020));

            var encontrado = new VehiculoControlador().buscarPorPlaca("QA1234").orElseThrow();
            assertEquals(registrado.getId(), encontrado.getId());
            assertEquals(cliente.getId(), encontrado.getClienteId());
            assertEquals("QA-1", new ClienteControlador().buscarPorId(encontrado.getClienteId())
                    .orElseThrow().getDocumentoIdentidad());
        }
    }

    @Test
    void creaOrdenReservaConsumeYLiberaYSeRecuperaTrasRollbackJdbc() throws Exception {
        var adaptador = ApiOpcional.clase(ADAPTADOR);
        try (var esquema = crearEsquema();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int vehiculo = registrarVehiculo();
            int orden = registrarOrden(esquema, vehiculo, "Recibido");
            esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
            var servicios = new ServicioControlador();
            var inventario = new InventarioControlador();
            int repuesto = inventario.registrarRepuesto("Filtro", "Bosch", 1, BigDecimal.TEN, 10, 0).id();

            var error = assertThrows(IllegalStateException.class, () -> transaccion(adaptador, esquema::conectar, port -> {
                port.reservar(new ReservaRepuesto(orden, repuesto, 2));
                port.reservar(new ReservaRepuesto(Integer.MAX_VALUE, repuesto, 1));
            }));
            assertInstanceOf(SQLIntegrityConstraintViolationException.class, error.getCause());
            assertEquals(10, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(0, reservas(esquema, orden, repuesto).size());

            transaccion(adaptador, esquema::conectar, port -> {
                port.reservar(new ReservaRepuesto(orden, repuesto, 5));
                port.consumir(new ConsumoRepuesto(orden, repuesto, 2));
                port.liberar(new ReservaRepuesto(orden, repuesto, 3));
            });
            assertEquals(8, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, orden, repuesto));
            assertEquals(vehiculo, servicios.buscarServicio(orden).orElseThrow().vehiculoId());
        }
    }

    @Test
    void mainContinuaTrasErrorJdbcYPermiteVolverDesdeTecnicos() throws Exception {
        try (var esquema = crearEsquema();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, 'Recibido')");
            var resultado = NavegacionAceptacionTest.ejecutar(NavegacionAceptacionTest.opcionServicios(), "1", "999", "1",
                    "2026-11-19 12:00", "", "Vehículo inexistente", "100", "1", "2", "999",
                    "8", "1", "999", "0", "0", "0");
            String salida = resultado.salida();
            int error = salida.indexOf("Error de base de datos.");
            int orden = salida.indexOf("No se encontró la orden 999.");
            int tecnico = salida.indexOf("No se encontró el técnico 999.");
            assertTrue(error >= 0 && orden > error && tecnico > orden, salida);
            assertEquals("CarMotors SGT", resultado.menus().get(resultado.menus().size() - 1));
        }
    }

    @Test
    void clasificaRecordatoriosYConservaLaNotificacionEntreConexiones() throws Exception {
        var tipo = ApiOpcional.clase("com.carmotors.controlador.RecordatorioControlador");
        try (var esquema = crearEsquema();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int vehiculo = registrarVehiculo();
            LocalDate hoy = LocalDate.now();
            var controlador = tipo.getConstructor().newInstance();
            var proximo = ApiOpcional.invocar(controlador, "crear",
                    new Class<?>[]{int.class, Integer.class, LocalDate.class, String.class},
                    vehiculo, null, hoy.plusDays(2), "Revisión próxima");
            int id = (int) ApiOpcional.invocar(proximo, "getId", SIN_ARGUMENTOS);
            // Una fila histórica permite probar los vencidos sin depender del reloj de la prueba.
            esquema.ejecutar("INSERT INTO recordatorios_mantenimiento "
                    + "(vehiculo_id, fecha_proximo_mantenimiento, descripcion) VALUES ("
                    + vehiculo + ", '" + hoy.minusDays(2) + "', 'Revisión vencida')");
            assertEquals(List.of(id), idsDeAlertas(ApiOpcional.invocar(controlador,
                    "listarAlertasProximas", new Class<?>[]{int.class}, 7)));
            var vencidos = idsDeAlertas(ApiOpcional.invocar(controlador, "listarAlertasVencidas", SIN_ARGUMENTOS));
            assertEquals(1, vencidos.size());
            assertNotEquals(id, vencidos.get(0));
            assertEquals(true, ApiOpcional.invocar(controlador, "notificar", new Class<?>[]{int.class}, id));

            var nuevaConsulta = tipo.getConstructor().newInstance();
            var persistido = (Optional<?>) ApiOpcional.invocar(nuevaConsulta, "buscarPorId",
                    new Class<?>[]{int.class}, id);
            assertEquals(true, ApiOpcional.invocar(persistido.orElseThrow(), "isNotificado", SIN_ARGUMENTOS));
            assertTrue(idsDeAlertas(ApiOpcional.invocar(nuevaConsulta,
                    "listarAlertasProximas", new Class<?>[]{int.class}, 7)).isEmpty());
        }
    }

    @Test
    void serviciosInvocaLaRutaNormalDeReservaConsumoYLiberacion() throws Exception {
        assumeTrue(java.util.Arrays.stream(ServicioControlador.class.getMethods())
                .anyMatch(metodo -> metodo.getName().equals("reservarRepuesto")),
                "Servicios aún no integra las operaciones de repuestos");
        try (var esquema = crearEsquema();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int vehiculo = registrarVehiculo();
            int orden = registrarOrden(esquema, vehiculo, "Recibido");
            esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
            var inventario = new InventarioControlador();
            int repuesto = inventario.registrarRepuesto("Filtro", "Bosch", 1, BigDecimal.TEN, 10, 0).id();

            var salida = NavegacionAceptacionTest.ejecutar(NavegacionAceptacionTest.opcionServicios(),
                    "9", Integer.toString(orden), Integer.toString(repuesto), "5",
                    "10", Integer.toString(orden), Integer.toString(repuesto), "2",
                    "11", Integer.toString(orden), Integer.toString(repuesto), "3", "0", "0").salida();
            assertFalse(salida.contains("Error de base de datos"), salida);
            assertFalse(salida.contains("Validación:"), salida);
            assertEquals(8, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, orden, repuesto));
        }
    }

    @Test
    void emiteFacturaConsultaYPreservaUnaSolaFacturaPorServicio() throws Exception {
        var tipo = controladorFacturas();
        try (var esquema = crearConsolidado();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int orden = registrarOrden(esquema, registrarVehiculo(), "Completado");
            registrarLineaDeRepuestos(esquema, orden, 2);
            Object facturas = tipo.getConstructor().newInstance();
            Class<?>[] idNumerico = {int.class};
            Object factura = ApiOpcional.invocar(facturas, "crear", idNumerico, orden);
            int id = (int) ApiOpcional.invocar(factura, "getId", SIN_ARGUMENTOS);
            var consulta = (Optional<?>) ApiOpcional.invocar(tipo.getConstructor().newInstance(),
                    "buscarPorId", idNumerico, id);
            assertEquals(orden, ApiOpcional.invocar(consulta.orElseThrow(), "getServicioId", SIN_ARGUMENTOS));
            assertEquals(new BigDecimal("30.00"),
                    ApiOpcional.invocar(consulta.orElseThrow(), "getTotalFinal", SIN_ARGUMENTOS));
            assertThrows(IllegalStateException.class,
                    () -> ApiOpcional.invocar(facturas, "crear", idNumerico, orden));
            var duplicado = assertThrows(SQLIntegrityConstraintViolationException.class, () -> esquema.ejecutar(
                    "INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos, "
                    + "total_mano_obra, impuestos, total_final) SELECT servicio_id, fecha_emision, "
                    + "total_repuestos, total_mano_obra, impuestos, total_final FROM facturas WHERE id = " + id));
            assertEquals(1062, duplicado.getErrorCode());
            assertEquals(1, ((List<?>) ApiOpcional.invocar(facturas, "listar", SIN_ARGUMENTOS)).size());
        }
    }

    @Test
    void facturaIgnoraUnaLineaLiberadaQueNoTuvoConsumo() throws Exception {
        var tipo = controladorFacturas();
        try (var esquema = crearConsolidado();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int orden = registrarOrden(esquema, registrarVehiculo(), "Completado");
            registrarLineaDeRepuestos(esquema, orden, 0);

            Object controlador = tipo.getConstructor().newInstance();
            Object factura = assertDoesNotThrow(
                    () -> ApiOpcional.invocar(controlador, "crear", new Class<?>[]{int.class}, orden));
            assertEquals(new BigDecimal("10.00"), ApiOpcional.invocar(factura, "getTotalFinal", SIN_ARGUMENTOS));
            assertTrue(((List<?>) ApiOpcional.invocar(factura, "getDetalles", SIN_ARGUMENTOS)).isEmpty());
        }
    }

    @Test
    void facturaUnaSolaVezBajoCompetenciaTrasConsumirUnaReservaReal() throws Exception {
        var tipo = controladorFacturas();
        try (var esquema = crearConsolidado();
             var conexion = new ConexionAplicacionPrueba(urlPrueba(), esquema.nombre())) {
            int vehiculo = registrarVehiculo();
            int orden = registrarOrden(esquema, vehiculo, "Recibido");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (2, 'Completado')");
            esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
            var inventario = new InventarioControlador();
            int repuesto = inventario.registrarRepuesto("Filtro", "Bosch", 1, BigDecimal.TEN, 10, 0).id();
            var servicios = new ServicioControlador();
            ApiOpcional.invocar(servicios, "reservarRepuesto", new Class<?>[]{ReservaRepuesto.class},
                    new ReservaRepuesto(orden, repuesto, 5));
            ApiOpcional.invocar(servicios, "consumirRepuesto", new Class<?>[]{ConsumoRepuesto.class},
                    new ConsumoRepuesto(orden, repuesto, 2));
            ApiOpcional.invocar(servicios, "liberarRepuesto", new Class<?>[]{ReservaRepuesto.class},
                    new ReservaRepuesto(orden, repuesto, 3));
            servicios.actualizarEstado(orden, 2);
            assertEquals(vehiculo, servicios.buscarServicio(orden).orElseThrow().vehiculoId());
            assertEquals(8, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, orden, repuesto));

            var inicio = new CyclicBarrier(2);
            Callable<Boolean> emitir = () -> {
                var controlador = tipo.getConstructor().newInstance();
                inicio.await(10, TimeUnit.SECONDS);
                try {
                    ApiOpcional.invocar(controlador, "crear", new Class<?>[]{int.class}, orden);
                    return true;
                } catch (SQLIntegrityConstraintViolationException e) {
                    assertEquals(1062, e.getErrorCode());
                    return false;
                } catch (IllegalStateException e) {
                    assertTrue(e.getMessage().contains("ya tiene factura"), e.getMessage());
                    return false;
                }
            };
            var ejecutor = Executors.newFixedThreadPool(2);
            try {
                var primera = ejecutor.submit(emitir);
                var segunda = ejecutor.submit(emitir);
                assertNotEquals(primera.get(15, TimeUnit.SECONDS),
                        segunda.get(15, TimeUnit.SECONDS),
                        "Exactamente una emisión debe tener éxito");
            } finally {
                ejecutor.shutdownNow();
                assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
            }
            var facturas = (List<?>) ApiOpcional.invocar(tipo.getConstructor().newInstance(), "listar", SIN_ARGUMENTOS);
            assertEquals(1, facturas.size());
            assertEquals(orden, ApiOpcional.invocar(facturas.get(0), "getServicioId", SIN_ARGUMENTOS));
            assertEquals(new BigDecimal("30.00"),
                    ApiOpcional.invocar(facturas.get(0), "getTotalFinal", SIN_ARGUMENTOS));
        }
    }

    private Class<?> controladorFacturas() throws ClassNotFoundException {
        assumeTrue(Files.isRegularFile(Path.of("database", "esquema", "esquema.sql")),
                "Falta el esquema consolidado de Facturación");
        var tipo = ApiOpcional.clase("com.carmotors.controlador.FacturaControlador");
        assumeTrue(java.util.Arrays.stream(tipo.getMethods()).anyMatch(metodo -> metodo.getName().equals("crear")),
                "Falta la API de emisión de Facturación");
        return tipo;
    }

    private EsquemaInventarioPrueba crearConsolidado() throws Exception {
        urlPrueba();
        return (EsquemaInventarioPrueba) EsquemaInventarioPrueba.class.getMethod("crearConsolidado").invoke(null);
    }

    private void registrarLineaDeRepuestos(EsquemaInventarioPrueba esquema, int orden, int usadas)
            throws SQLException {
        esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
        var inventario = new InventarioControlador();
        int repuesto = inventario.registrarRepuesto("Filtro", "Bosch", 1, BigDecimal.TEN, 10, 0).id();
        esquema.ejecutar("INSERT INTO servicios_repuestos "
                + "(servicio_id, repuesto_id, cantidad_reservada, cantidad_usada, precio_unitario_venta) VALUES ("
                + orden + ", " + repuesto + ", 0, " + usadas + ", 10)");
    }

    private int registrarOrden(EsquemaInventarioPrueba esquema, int vehiculo, String estado) throws SQLException {
        esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
        esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, '" + estado + "')");
        return Math.toIntExact(new ServicioControlador().registrarServicio(new Servicio(null, vehiculo, 1,
                LocalDateTime.of(2026, 11, 19, 12, 0), null, "Cambio de filtro", BigDecimal.TEN, 1)).valor());
    }

    private static List<Integer> idsDeAlertas(Object resultado) throws Exception {
        var ids = new java.util.ArrayList<Integer>();
        for (Object alerta : (List<?>) resultado) {
            Object recordatorio = ApiOpcional.invocar(alerta, "getRecordatorio", SIN_ARGUMENTOS);
            ids.add((int) ApiOpcional.invocar(recordatorio, "getId", SIN_ARGUMENTOS));
        }
        return ids;
    }

    private int registrarVehiculo() throws SQLException {
        int cliente = new ClienteControlador().registrar("Cliente QA", "QA-1", null, "qa@example.test").getId();
        return new VehiculoControlador().registrarVehiculo(new Vehiculo(cliente, "QA1234", "Mazda", "3", 2020))
                .getId();
    }

    private List<Integer> reservas(EsquemaInventarioPrueba esquema, int orden, int repuesto) throws SQLException {
        try (var conexion = esquema.conectar();
             var consulta = conexion.prepareStatement("SELECT cantidad_reservada, cantidad_usada "
                     + "FROM servicios_repuestos WHERE servicio_id = ? AND repuesto_id = ?")) {
            consulta.setInt(1, orden);
            consulta.setInt(2, repuesto);
            try (var fila = consulta.executeQuery()) {
                return fila.next() ? List.of(fila.getInt(1), fila.getInt(2)) : List.of();
            }
        }
    }

    private void transaccion(Class<?> adaptador, FuenteConexion fuente, Consumer<InventarioServiciosPort> accion)
            throws Exception {
        Class<?> operacion = Class.forName(ADAPTADOR + "$OperacionConInventario");
        Object callback = Proxy.newProxyInstance(operacion.getClassLoader(), new Class<?>[]{operacion},
                (proxy, metodo, args) -> {
                    assertFalse(((Connection) args[0]).getAutoCommit());
                    accion.accept((InventarioServiciosPort) args[1]);
                    return null;
                });
        try {
            adaptador.getMethod("ejecutarEnTransaccion", FuenteConexion.class, operacion)
                    .invoke(null, fuente, callback);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception causa) {
                throw causa;
            }
            throw e;
        }
    }

    private EsquemaInventarioPrueba crearEsquema() throws Exception {
        urlPrueba();
        var esquema = EsquemaInventarioPrueba.crear();
        try {
            var clientes = ddl("clientes");
            esquema.ejecutar(clientes.get(0));
            for (String sentencia : ddl("vehiculos")) {
                esquema.ejecutar(sentencia);
            }
            for (String sentencia : ddl("servicios")) {
                esquema.ejecutar(sentencia);
            }
            for (String sentencia : clientes.subList(1, clientes.size())) {
                esquema.ejecutar(sentencia);
            }
            return esquema;
        } catch (Exception | AssertionError e) {
            try {
                esquema.close();
            } catch (SQLException limpieza) {
                e.addSuppressed(limpieza);
            }
            throw e;
        }
    }

    private List<String> ddl(String bloque) throws Exception {
        String script = Files.readString(Path.of("database", "bloques", bloque + ".sql"));
        String sql = String.join("\n", script.lines().filter(linea -> !linea.stripLeading().startsWith("--")).toList());
        return java.util.Arrays.stream(sql.split(";")).map(String::strip)
                .filter(linea -> !linea.isEmpty() && !linea.equalsIgnoreCase("use carmotors"))
                .peek(linea -> assertTrue(linea.startsWith("CREATE TABLE ") || linea.startsWith("ALTER TABLE "),
                        "Se esperaba DDL de tablas en el bloque " + bloque))
                .toList();
    }

    private String urlPrueba() {
        String url = System.getenv(EsquemaInventarioPrueba.VARIABLE_URL);
        assertTrue(url.matches("jdbc:mysql://[^/]+/"), "Configura la URL del servidor de prueba sin catálogo ni opciones");
        return url;
    }
}
