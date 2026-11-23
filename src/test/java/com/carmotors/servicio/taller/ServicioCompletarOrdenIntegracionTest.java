package com.carmotors.servicio.taller;

import com.carmotors.dao.EsquemaInventarioPrueba;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.servicio.taller.ServicioService.RepuestoSolicitado;
import com.carmotors.servicio.taller.integracion.ConsumoRepuesto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Al completar una orden, las unidades reservadas que no se consumieron vuelven al stock
 * (igual que al cancelarla). Lo usado no cambia y la factura cobra solo lo usado.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class ServicioCompletarOrdenIntegracionTest {

    private static final int RECIBIDO = 1;
    private static final int EN_PROCESO = 2;
    private static final int COMPLETADO = 3;
    private static final int STOCK_INICIAL = 10;

    private EsquemaInventarioPrueba db;
    private ServicioService servicios;

    @BeforeEach
    void preparar() throws Exception {
        db = EsquemaInventarioPrueba.crearConsolidado();
        db.ejecutar("INSERT INTO clientes (id, nombre, documento_identidad, email) VALUES (1, 'Cliente', '123', 'cliente@example.com')");
        db.ejecutar("INSERT INTO vehiculos (id, cliente_id, placa, marca, modelo, anio) VALUES (1, 1, 'ABC123', 'Marca', 'Modelo', 2020)");
        db.ejecutar("INSERT INTO servicio_estados (id, nombre) VALUES (1, 'Recibido'), (2, 'En proceso'), (3, 'Completado')");
        db.ejecutar("INSERT INTO mantenimiento_tipos (id, nombre) VALUES (1, 'Preventivo')");
        db.ejecutar("INSERT INTO repuesto_tipos (id, nombre) VALUES (1, 'Filtros')");
        db.ejecutar("INSERT INTO repuestos (id, nombre, marca, repuesto_tipo_id, precio_venta, cantidad_stock) "
                + "VALUES (1, 'Filtro de aceite', 'Bosch', 1, 25000, " + STOCK_INICIAL + ")");
        servicios = new ServicioService(new ServicioDAO(), new TecnicoDAO(), db::conectar);
    }

    @AfterEach
    void limpiar() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    private int nuevaOrdenConReserva(int cantidad) throws Exception {
        Servicio orden = new Servicio(null, 1, 1, LocalDateTime.of(2026, 11, 20, 8, 0), null,
                "Cambio de aceite", new BigDecimal("30000.00"), RECIBIDO);
        return Math.toIntExact(servicios.registrarServicioConReservas(
                orden, List.of(new RepuestoSolicitado(1, cantidad))).valor());
    }

    private int entero(String sql) throws Exception {
        try (var conexion = db.conectar(); var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getInt(1);
        }
    }

    @Test
    void alCompletarSeDevuelvenAlStockLasUnidadesReservadasNoUsadas() throws Exception {
        int id = nuevaOrdenConReserva(3);
        servicios.consumirRepuesto(new ConsumoRepuesto(id, 1, 2));
        assertEquals(STOCK_INICIAL - 3, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"));

        servicios.actualizarEstado(id, COMPLETADO);

        assertEquals(STOCK_INICIAL - 2, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"),
                "La unidad reservada que no se usó vuelve al stock");
        assertEquals(0, entero("SELECT cantidad_reservada FROM servicios_repuestos WHERE servicio_id = " + id));
        assertEquals(2, entero("SELECT cantidad_usada FROM servicios_repuestos WHERE servicio_id = " + id),
                "Lo usado no cambia");
        assertEquals(COMPLETADO, entero("SELECT estado_id FROM servicios WHERE id = " + id));
    }

    @Test
    void completarDosVecesNoDevuelveUnidadesDeMas() throws Exception {
        int id = nuevaOrdenConReserva(4);
        servicios.consumirRepuesto(new ConsumoRepuesto(id, 1, 1));

        servicios.actualizarEstado(id, COMPLETADO);
        servicios.actualizarEstado(id, COMPLETADO);

        assertEquals(STOCK_INICIAL - 1, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"));
        assertEquals(STOCK_INICIAL, entero("SELECT cantidad_stock + cantidad_reservada + cantidad_usada "
                + "FROM repuestos, servicios_repuestos WHERE repuestos.id = 1 AND servicio_id = " + id),
                "bodega + reservado + usado = unidades iniciales");
    }

    @Test
    void pasarAEnProcesoConservaLasReservas() throws Exception {
        int id = nuevaOrdenConReserva(3);

        servicios.actualizarEstado(id, EN_PROCESO);

        assertEquals(STOCK_INICIAL - 3, entero("SELECT cantidad_stock FROM repuestos WHERE id = 1"));
        assertEquals(3, entero("SELECT cantidad_reservada FROM servicios_repuestos WHERE servicio_id = " + id));
    }
}
