package com.carmotors.aceptacion;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.controlador.FacturaControlador;
import com.carmotors.controlador.InventarioControlador;
import com.carmotors.controlador.RecordatorioControlador;
import com.carmotors.controlador.ServicioControlador;
import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.FacturaDAO;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RecordatorioDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Alerta;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicio.taller.ServicioService;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicio.taller.integracion.ReservaRepuesto;
import com.carmotors.servicios.clientes.ClienteService;
import com.carmotors.servicios.facturacion.FacturaService;
import com.carmotors.servicios.inventario.InventarioService;
import com.carmotors.servicios.inventario.InventarioServiciosJdbc;
import com.carmotors.servicios.recordatorios.RecordatorioService;
import com.carmotors.servicios.vehiculos.VehiculoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class FlujosJdbcAceptacionTest {
    @TempDir
    Path temporal;

    @Test
    void muestraLosPromediosDeOrdenesCompletadasConEntregaDesdeMysql() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int vehiculo = registrarVehiculo(esquema).getId();
            cargarCatalogos(esquema, "Completado");
            esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (2, 'Correctivo')");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (2, 'En proceso'), (3, 'Finalizado')");
            var recepcion = LocalDateTime.of(2026, 11, 19, 22, 0);
            var dao = new ServicioDAO();
            try (var conexion = esquema.conectar()) {
                dao.registrar(conexion, new Servicio(null, vehiculo, 1, recepcion,
                        recepcion.plusHours(4), "Revisión", null, 1));
                dao.registrar(conexion, new Servicio(null, vehiculo, 1, recepcion,
                        recepcion.plusHours(5).plusMinutes(4), "Revisión", null, 3));
                dao.registrar(conexion, new Servicio(null, vehiculo, 1, recepcion,
                        recepcion.plusDays(10), "Entrega prevista", null, 2));
                dao.registrar(conexion, new Servicio(null, vehiculo, 2, recepcion,
                        null, "Entrega pendiente de registro", null, 1));
            }

            String salida = main(esquema, "3", "12", "0", "0");
            assertTrue(salida.contains("Preventivo            | 0 Días, 4 Horas, 32 Minutos"), salida);
            assertTrue(salida.contains("Correctivo            | Sin servicios completados con ambas fechas"), salida);
        }
    }

    @Test
    void guardaLaEstimacionPorOrdenYLaReutilizaAlCambiarElHistorial() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int vehiculo = registrarVehiculo(esquema).getId();
            cargarCatalogos(esquema, "Finalizado");
            esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (2, 'Correctivo')");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (2, 'Pendiente')");
            var recepcion = LocalDateTime.of(2026, 11, 19, 22, 0);
            var dao = new ServicioDAO();
            long historial;
            long orden;
            long sinHistorial;
            long conFechaProvisional;
            try (var conexion = esquema.conectar()) {
                historial = dao.registrar(conexion, new Servicio(null, vehiculo, 1, recepcion.minusDays(1),
                        recepcion.minusDays(1).plusHours(5).plusMinutes(30), "Historial", null, 1)).valor();
                orden = dao.registrar(conexion, new Servicio(null, vehiculo, 1, recepcion,
                        null, "Pendiente", null, 2)).valor();
                sinHistorial = dao.registrar(conexion, new Servicio(null, vehiculo, 2, recepcion,
                        null, "Pendiente", null, 2)).valor();
                conFechaProvisional = dao.registrar(conexion, new Servicio(null, vehiculo, 2, recepcion,
                        recepcion.plusDays(1).plusHours(3).plusMinutes(30), "Entrega prevista", null, 2)).valor();
            }

            String salida = main(esquema, "8", "1", Long.toString(orden),
                    "1", Long.toString(sinHistorial), "1", Long.toString(conFechaProvisional), "2", "0", "0");
            assertTrue(salida.contains("Tiempo estimado de espera: 0 Días, 5 Horas, 30 Minutos"), salida);
            assertTrue(salida.contains("Fecha estimada de entrega: 2026-11-20 03:30"), salida);
            assertTrue(salida.contains("No hay órdenes finalizadas con ambas fechas para estimar"), salida);
            assertTrue(salida.contains("Estimación provisional basada en la fecha de entrega registrada"), salida);
            assertTrue(salida.contains("Tiempo estimado de espera: 1 Días, 3 Horas, 30 Minutos"), salida);
            assertTrue(salida.contains("Fecha estimada de entrega: 2026-11-21 01:30"), salida);
            assertTrue(salida.contains("INFORME DE TIEMPOS PROMEDIO DE REPARACIÓN"), salida);

            try (var conexion = esquema.conectar();
                 var consulta = conexion.prepareStatement(
                         "SELECT servicio_id, duracion_estimada_segundos FROM tiempo_entrega");
                 var filas = consulta.executeQuery()) {
                assertTrue(filas.next());
                assertEquals(orden, filas.getLong("servicio_id"));
                assertEquals(19_800, filas.getLong("duracion_estimada_segundos"));
                assertFalse(filas.next());
            }
            esquema.ejecutar("UPDATE servicios SET fecha_entrega = DATE_ADD(fecha_entrega, INTERVAL 1 HOUR)"
                    + " WHERE id = " + historial);
            String segundaConsulta = main(esquema, "8", "1", Long.toString(orden), "0", "0");
            assertTrue(segundaConsulta.contains("Fecha estimada de entrega: 2026-11-20 03:30"), segundaConsulta);
        }
    }

    @Test
    void registraClienteYVehiculoYCompruebaLaRelacionPersistida() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            var registrado = registrarVehiculo(esquema);
            var encontrado = vehiculos(esquema).buscarPorPlaca("QA1234").orElseThrow();
            assertEquals(registrado.getId(), encontrado.getId());
            assertEquals(registrado.getClienteId(), encontrado.getClienteId());
            assertEquals("QA-1", clientes(esquema).buscarPorId(encontrado.getClienteId())
                    .orElseThrow().getDocumentoIdentidad());
        }
    }

    @Test
    void creaOrdenReservaConsumeYLiberaYSeRecuperaTrasRollbackJdbc() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int vehiculo = registrarVehiculo(esquema).getId();
            int orden = registrarOrden(esquema, vehiculo, "Recibido");
            int repuesto = crearRepuesto(esquema);
            var inventario = inventario(esquema);

            var error = assertThrows(IllegalStateException.class, () ->
                    InventarioServiciosJdbc.ejecutarEnTransaccion(esquema::conectar, (conexion, port) -> {
                        assertFalse(conexion.getAutoCommit());
                        port.reservar(new ReservaRepuesto(orden, repuesto, 2));
                        port.reservar(new ReservaRepuesto(Integer.MAX_VALUE, repuesto, 1));
                        return null;
                    }));
            assertInstanceOf(SQLIntegrityConstraintViolationException.class, error.getCause());
            assertEquals(10, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(), reservas(esquema, orden, repuesto));

            InventarioServiciosJdbc.ejecutarEnTransaccion(esquema::conectar, (conexion, port) -> {
                assertFalse(conexion.getAutoCommit());
                port.reservar(new ReservaRepuesto(orden, repuesto, 5));
                port.consumir(new ConsumoRepuesto(orden, repuesto, 2));
                port.liberar(new ReservaRepuesto(orden, repuesto, 3));
                return null;
            });
            assertEquals(8, inventario.buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, orden, repuesto));
        }
    }

    @Test
    void mainContinuaTrasErrorJdbcYPermiteVolverDesdeTecnicos() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            cargarCatalogos(esquema, "Recibido");
            String salida = main(esquema, "3", "1", "999", "1", "2026-11-19 12:00", "",
                    "Vehículo inexistente", "100", "1", "2", "999", "8", "1", "999", "0", "0", "0");
            int error = salida.indexOf("Error de base de datos.");
            int orden = salida.indexOf("No se encontró la orden 999.");
            int tecnico = salida.indexOf("No se encontró el técnico 999.");
            assertTrue(error >= 0 && orden > error && tecnico > orden, salida);
            assertTrue(salida.lastIndexOf("=== CarMotors SGT ===") > tecnico, salida);
        }
    }

    @Test
    void clasificaRecordatoriosYConservaLaNotificacionEntreConexiones() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int vehiculo = registrarVehiculo(esquema).getId();
            LocalDate hoy = LocalDate.now();
            String salida = main(esquema, "6", "1", Integer.toString(vehiculo), "", hoy.plusDays(2).toString(),
                    "Revisión próxima", "0", "0");
            assertTrue(salida.contains("Creado:"), salida);
            var controlador = recordatorios(esquema);
            int id = controlador.listarPendientes().get(0).getId();
            esquema.ejecutar("INSERT INTO recordatorios_mantenimiento "
                    + "(vehiculo_id, fecha_proximo_mantenimiento, descripcion) VALUES ("
                    + vehiculo + ", '" + hoy.minusDays(2) + "', 'Revisión vencida')");
            assertEquals(List.of(id), controlador.listarAlertasProximas(7).stream()
                    .map(Alerta::getRecordatorio).map(r -> r.getId()).toList());
            var vencidos = controlador.listarAlertasVencidas();
            assertEquals(1, vencidos.size());
            assertNotEquals(id, vencidos.get(0).getRecordatorio().getId());
            assertTrue(main(esquema, "6", "9", Integer.toString(id), "0", "0").contains("Marcado como notificado."));
            assertTrue(recordatorios(esquema).buscarPorId(id).orElseThrow().isNotificado());
            assertTrue(recordatorios(esquema).listarAlertasProximas(7).isEmpty());
        }
    }

    @Test
    void serviciosInvocaLaRutaNormalDeReservaConsumoYLiberacion() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            String orden = Integer.toString(registrarOrden(esquema, registrarVehiculo(esquema).getId(), "Recibido"));
            String repuesto = Integer.toString(crearRepuesto(esquema));
            String salida = main(esquema, "3", "9", orden, repuesto, "5", "10", orden, repuesto, "2",
                    "11", orden, repuesto, "3", "0", "0");
            assertFalse(salida.contains("Error de base de datos"), salida);
            assertFalse(salida.contains("Validación:"), salida);
            assertEquals(8, inventario(esquema).buscarRepuesto(Integer.parseInt(repuesto)).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, Integer.parseInt(orden), Integer.parseInt(repuesto)));
        }
    }

    @Test
    void emiteFacturaConsultaYPreservaUnaSolaFacturaPorServicio() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int orden = registrarOrden(esquema, registrarVehiculo(esquema).getId(), "Completado");
            registrarLineaDeRepuestos(esquema, orden, 2);
            String salida = main(esquema, "7", "1", Integer.toString(orden), "0", "0");
            assertTrue(salida.contains("FACTURA") && salida.contains("30.00"), salida);
            var factura = facturas(esquema).listar().get(0);
            var consulta = facturas(esquema).buscarPorId(factura.getId()).orElseThrow();
            assertEquals(orden, consulta.getServicioId());
            assertEquals(new BigDecimal("30.00"), consulta.getTotalFinal());
            assertThrows(IllegalStateException.class, () -> facturas(esquema).crear(orden));
            var duplicado = assertThrows(SQLIntegrityConstraintViolationException.class, () -> esquema.ejecutar(
                    "INSERT INTO facturas (servicio_id, fecha_emision, total_repuestos, "
                    + "total_mano_obra, impuestos, total_final) SELECT servicio_id, fecha_emision, "
                    + "total_repuestos, total_mano_obra, impuestos, total_final FROM facturas WHERE id = " + factura.getId()));
            assertEquals(1062, duplicado.getErrorCode());
            assertEquals(1, facturas(esquema).listar().size());
        }
    }

    @Test
    void facturaIgnoraUnaLineaLiberadaQueNoTuvoConsumo() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int orden = registrarOrden(esquema, registrarVehiculo(esquema).getId(), "Completado");
            registrarLineaDeRepuestos(esquema, orden, 0);

            var factura = assertDoesNotThrow(() -> facturas(esquema).crear(orden));
            assertEquals(new BigDecimal("10.00"), factura.getTotalFinal());
            assertTrue(factura.getDetalles().isEmpty());
        }
    }

    @Test
    void facturaUnaSolaVezBajoCompetenciaTrasConsumirUnaReservaReal() throws Exception {
        try (var esquema = EsquemaInventarioPrueba.crearConsolidado()) {
            int orden = registrarOrden(esquema, registrarVehiculo(esquema).getId(), "Recibido");
            esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (2, 'Completado')");
            int repuesto = crearRepuesto(esquema);
            var servicios = new ServicioControlador(new ServicioService(
                    new ServicioDAO(), new TecnicoDAO(), esquema::conectar));
            servicios.reservarRepuesto(new ReservaRepuesto(orden, repuesto, 5));
            servicios.consumirRepuesto(new ConsumoRepuesto(orden, repuesto, 2));
            servicios.liberarRepuesto(new ReservaRepuesto(orden, repuesto, 3));
            servicios.actualizarEstado(orden, 2);
            assertEquals(8, inventario(esquema).buscarRepuesto(repuesto).orElseThrow().cantidadStock());
            assertEquals(List.of(0, 2), reservas(esquema, orden, repuesto));

            var inicio = new CyclicBarrier(2);
            Callable<Boolean> emitir = () -> {
                var controlador = facturas(esquema);
                inicio.await(10, TimeUnit.SECONDS);
                try {
                    controlador.crear(orden);
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
                assertNotEquals(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS),
                        "Exactamente una emisión debe tener éxito");
            } finally {
                ejecutor.shutdownNow();
                assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
            }
            var facturas = facturas(esquema).listar();
            assertEquals(1, facturas.size());
            assertEquals(orden, facturas.get(0).getServicioId());
            assertEquals(new BigDecimal("30.00"), facturas.get(0).getTotalFinal());
        }
    }

    private String main(EsquemaInventarioPrueba esquema, String... lineas) throws Exception {
        return SesionMainPrueba.ejecutar(temporal, esquema, String.join("\n", lineas));
    }

    private static ClienteControlador clientes(EsquemaInventarioPrueba esquema) {
        return new ClienteControlador(new ClienteService(new ClienteDAO(esquema::conectar)));
    }

    private static VehiculoControlador vehiculos(EsquemaInventarioPrueba esquema) {
        return new VehiculoControlador(new VehiculoService(
                new VehiculoDAO(esquema::conectar), new ClienteDAO(esquema::conectar)));
    }

    private static InventarioControlador inventario(EsquemaInventarioPrueba esquema) {
        return new InventarioControlador(new InventarioService(
                new RepuestoDAO(esquema::conectar), new ProveedorDAO(esquema::conectar)));
    }

    private static RecordatorioControlador recordatorios(EsquemaInventarioPrueba esquema) {
        return new RecordatorioControlador(new RecordatorioService(new RecordatorioDAO(esquema::conectar)));
    }

    private static FacturaControlador facturas(EsquemaInventarioPrueba esquema) {
        return new FacturaControlador(new FacturaService(new FacturaDAO(esquema::conectar), BigDecimal.ZERO));
    }

    private static Vehiculo registrarVehiculo(EsquemaInventarioPrueba esquema) throws SQLException {
        int cliente = clientes(esquema).registrar("Cliente QA", "QA-1", null, "qa@example.test").getId();
        return vehiculos(esquema).registrarVehiculo(new Vehiculo(cliente, "qa1234", "Mazda", "3", 2020));
    }

    private static void cargarCatalogos(EsquemaInventarioPrueba esquema, String estado) throws SQLException {
        esquema.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
        esquema.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, '" + estado + "')");
    }

    private static int registrarOrden(EsquemaInventarioPrueba esquema, int vehiculo, String estado) throws SQLException {
        cargarCatalogos(esquema, estado);
        try (var conexion = esquema.conectar()) {
            return Math.toIntExact(new ServicioDAO().registrar(conexion, new Servicio(null, vehiculo, 1,
                    LocalDateTime.of(2026, 11, 19, 12, 0), null, "Cambio de filtro", BigDecimal.TEN, 1)).valor());
        }
    }

    private static int crearRepuesto(EsquemaInventarioPrueba esquema) throws SQLException {
        esquema.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
        return inventario(esquema).registrarRepuesto("Filtro", "Bosch", 1, BigDecimal.TEN, 10, 0).id();
    }

    private static void registrarLineaDeRepuestos(EsquemaInventarioPrueba esquema, int orden, int usadas)
            throws SQLException {
        int repuesto = crearRepuesto(esquema);
        esquema.ejecutar("INSERT INTO servicios_repuestos "
                + "(servicio_id, repuesto_id, cantidad_reservada, cantidad_usada, precio_unitario_venta) VALUES ("
                + orden + ", " + repuesto + ", 0, " + usadas + ", 10)");
    }

    private static List<Integer> reservas(EsquemaInventarioPrueba esquema, int orden, int repuesto) throws SQLException {
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
}
