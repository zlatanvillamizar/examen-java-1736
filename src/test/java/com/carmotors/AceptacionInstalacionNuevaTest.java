package com.carmotors;

import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.FacturaDAO;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.Repuesto;
import com.carmotors.modelo.Servicio;
import com.carmotors.servicio.taller.ServicioService;
import com.carmotors.servicio.taller.ServicioService.RepuestoSolicitado;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import com.carmotors.servicios.facturacion.FacturaService;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Una instalación nueva (esquema consolidado + scripts de datos documentados en el README)
 * permite el ciclo completo del taller: orden con repuestos, completar y facturar.
 */
@EnabledIfEnvironmentVariable(named = EsquemaInventarioPrueba.VARIABLE_URL, matches = ".+")
class AceptacionInstalacionNuevaTest {

    private static final Path CATALOGOS_SERVICIOS = Path.of("database", "datos", "catalogos_servicios.sql");
    private static final Path TIPOS_REPUESTO = Path.of("docs", "inventario", "repuesto_tipos_iniciales.sql");

    private EsquemaInventarioPrueba db;

    @BeforeEach
    void instalar() throws Exception {
        db = EsquemaInventarioPrueba.crearConsolidado();
    }

    @AfterEach
    void limpiar() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    /** Ejecuta un script de datos como lo haría el cliente mysql: sin comentarios, por sentencias. */
    private void cargar(Path script) throws Exception {
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            if (!linea.strip().startsWith("--")) {
                sinComentarios.append(linea).append('\n');
            }
        }
        try (Connection conexion = db.conectar(); Statement sentencias = conexion.createStatement()) {
            for (String sentencia : sinComentarios.toString().split(";")) {
                if (!sentencia.isBlank()) {
                    assertFalse(sentencia.toUpperCase().matches("(?s).*\\b(CREATE|ALTER|DROP|TRUNCATE|DELETE)\\b.*"),
                            "Un script de datos no debe modificar el esquema ni borrar datos");
                    sentencias.execute(sentencia);
                }
            }
        }
    }

    private int entero(String sql) throws Exception {
        try (Connection c = db.conectar(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private int idPorNombre(String tabla, String nombre) throws Exception {
        try (Connection c = db.conectar();
             PreparedStatement s = c.prepareStatement("SELECT id FROM " + tabla + " WHERE nombre = ?")) {
            s.setString(1, nombre);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        }
    }

    @Test
    void losCatalogosSeCarganSinDuplicarAunqueSeRepitanLosScripts() throws Exception {
        assertEquals(0, entero("SELECT COUNT(*) FROM servicio_estados"), "El esquema no trae estados");

        cargar(CATALOGOS_SERVICIOS);
        cargar(CATALOGOS_SERVICIOS);
        cargar(TIPOS_REPUESTO);
        cargar(TIPOS_REPUESTO);

        assertEquals(4, entero("SELECT COUNT(*) FROM servicio_estados"));
        assertEquals(2, entero("SELECT COUNT(*) FROM mantenimiento_tipos"));
        assertEquals(6, entero("SELECT COUNT(*) FROM repuesto_tipos"));
        assertEquals(1, entero("SELECT COUNT(*) FROM servicio_estados WHERE nombre = 'Completado'"),
                "Facturación exige el estado 'Completado'");
        assertEquals(1, entero("SELECT COUNT(*) FROM servicio_estados WHERE nombre = 'Cancelado'"),
                "Servicios reconoce la cancelación por 'Cancelado'");
    }

    @Test
    void unaInstalacionNuevaPuedeRegistrarCompletarYFacturarUnaOrden() throws Exception {
        cargar(CATALOGOS_SERVICIOS);
        cargar(TIPOS_REPUESTO);
        db.ejecutar("INSERT INTO clientes (nombre, documento_identidad, email) VALUES ('Ana Pérez', '1001', 'ana@correo.com')");
        db.ejecutar("INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) VALUES (1, 'ABC123', 'Mazda', '3', 2020)");

        InventarioService inventario = new InventarioService(new RepuestoDAO(db::conectar), new ProveedorDAO(db::conectar));
        Repuesto filtro = inventario.registrarRepuesto("Filtro de aceite", "Bosch",
                idPorNombre("repuesto_tipos", "Filtros"), new BigDecimal("25000.00"), 10, 3);

        ServicioService servicios = new ServicioService(new ServicioDAO(), new TecnicoDAO(), db::conectar);
        Servicio orden = new Servicio(null, 1, idPorNombre("mantenimiento_tipos", "Preventivo"),
                LocalDateTime.of(2026, 11, 20, 8, 0), null, "Cambio de aceite", new BigDecimal("30000.00"),
                idPorNombre("servicio_estados", "Recibido"));
        int id = Math.toIntExact(servicios.registrarServicioConReservas(
                orden, List.of(new RepuestoSolicitado(filtro.id(), 3))).valor());
        servicios.consumirRepuesto(new ConsumoRepuesto(id, filtro.id(), 2));
        servicios.actualizarEstado(id, idPorNombre("servicio_estados", "Completado"));

        Factura factura = new FacturaService(new FacturaDAO(db::conectar), BigDecimal.ZERO).crear(id);

        assertEquals(new BigDecimal("50000.00"), factura.getTotalRepuestos(), "Se cobran solo las 2 unidades usadas");
        assertEquals(new BigDecimal("80000.00"), factura.getTotalFinal(), "Repuestos + mano de obra");
        assertEquals(8, inventario.buscarRepuesto(filtro.id()).orElseThrow().cantidadStock(),
                "La unidad reservada y no usada volvió al stock al completar la orden");
    }
}
