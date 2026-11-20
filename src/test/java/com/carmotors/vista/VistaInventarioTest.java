package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.ProveedoresEnMemoria;
import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.RepuestosEnMemoria;
import com.carmotors.modelo.Repuesto;
import com.carmotors.dao.ReservaRepuestoDAO;
import com.carmotors.servicios.inventario.ConsultaReservas;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vista de consola de Inventario con DAO en memoria (sin MySQL). */
class VistaInventarioTest {

    private final RepuestosEnMemoria repuestos = new RepuestosEnMemoria();

    private String ejecutar(RepuestoDAO dao, String... lineas) {
        return ejecutar(dao, servicioId -> {
            throw new SQLException("Consulta de reservas no configurada en esta prueba");
        }, lineas);
    }

    private String ejecutar(RepuestoDAO dao, ConsultaReservas consulta, String... lineas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            Scanner entrada = new Scanner(String.join("\n", lineas));
            InventarioControlador controlador =
                    new InventarioControlador(new InventarioService(dao, new ProveedoresEnMemoria(), consulta));
            new VistaInventario(entrada, salida, controlador).mostrar();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String ejecutar(String... lineas) {
        return ejecutar(repuestos, lineas);
    }

    private void filtroConStock(int stock) {
        repuestos.registrar(Repuesto.nuevo("Filtro de aceite", "Bosch", 1, new BigDecimal("25000.00"), stock, 2));
    }

    @Test
    void registraUnRepuestoYLoMuestraAlBuscarloYListarlo() {
        String salida = ejecutar("1", "Filtro de aceite", "Bosch", "1", "25000.50", "10", "2", "2", "1", "3", "0");

        assertTrue(salida.contains("Repuesto registrado con ID 1."), salida);
        assertTrue(salida.contains("Repuesto 1 | Filtro de aceite | Marca: Bosch | Tipo: 1 | Precio: 25000.50 | Stock: 10 | Mínimo: 2"));
        assertEquals(1, repuestos.cantidadGuardados());
    }

    @Test
    void consultaElStockDisponibleYAlertaSiEstaEnElMinimo() {
        filtroConStock(2);

        String salida = ejecutar("4", "1", "4", "9", "0");

        assertTrue(salida.contains("Repuesto 1 | Filtro de aceite | Stock disponible: 2 | Stock mínimo: 2"), salida);
        assertTrue(salida.contains("excluye las unidades reservadas"));
        assertTrue(salida.contains("Alerta: el repuesto está en su stock mínimo"));
        assertTrue(salida.contains("No se encontró el repuesto 9."));
    }

    @Test
    void registraEntradasYSalidasValidas() {
        filtroConStock(5);

        String salida = ejecutar("5", "1", "3", "6", "1", "6", "0");

        assertTrue(salida.contains("Entrada registrada. Stock actual: 8."), salida);
        assertTrue(salida.contains("Salida registrada. Stock actual: 2."));
        assertEquals(2, repuestos.stock(1));
    }

    @Test
    void unaSalidaSinStockSuficienteMuestraElErrorYNoCambiaNada() {
        filtroConStock(3);

        String salida = ejecutar("6", "1", "4", "0");

        assertTrue(salida.contains("Stock insuficiente: se pidieron 4 y hay 3 disponibles. No se registró el movimiento."),
                salida);
        assertEquals(3, repuestos.stock(1));
        assertEquals(0, repuestos.movimientos(), "La salida se rechaza antes de llegar al DAO");
    }

    @Test
    void movimientosSobreUnRepuestoInexistenteMuestranUnMensajeClaro() {
        String salida = ejecutar("5", "7", "1", "6", "7", "1", "0");

        assertEquals(2, salida.split("No existe el repuesto con ID 7.", -1).length - 1, salida);
    }

    @Test
    void validaLasEntradasSinSalirDelMenu() {
        filtroConStock(5);

        String salida = ejecutar("x", "9", "2", "abc", "2", "0", "5", "1", "-3", "6", "1", "0",
                "1", "Filtro", "Bosch", "1", "12,5",
                "1", "Filtro", "Bosch", "1", "-1",
                "1", "", "1", "Filtro", "Bosch", "1", "10.999", "1", "1",
                "1", "Filtro", "Bosch", "1", "10", "-2",
                "2", "1", "0");

        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 8."), salida);
        assertTrue(salida.contains("ID del repuesto debe ser un número entero."));
        assertTrue(salida.contains("ID del repuesto debe ser positivo."));
        assertTrue(salida.contains("Cantidad que ingresa debe ser positivo."));
        assertTrue(salida.contains("Cantidad que sale debe ser positivo."));
        assertTrue(salida.contains("Precio de venta debe ser un número con punto decimal"));
        assertTrue(salida.contains("Precio de venta no puede ser negativo."));
        assertTrue(salida.contains("Nombre es obligatorio."));
        assertTrue(salida.contains("Validación: El precio de venta debe caber en DECIMAL(10,2)"));
        assertTrue(salida.contains("Stock inicial no puede ser negativo."));
        assertTrue(salida.lastIndexOf("Repuesto 1 | Filtro de aceite") > salida.lastIndexOf("Validación"),
                "El menú sigue funcionando después de los errores");
        assertEquals(1, repuestos.cantidadGuardados(), "Ningún dato inválido se guardó");
        assertEquals(5, repuestos.stock(1));
    }

    @Test
    void listasVaciasYStockMinimo() {
        String vacia = ejecutar("3", "7", "0");
        assertTrue(vacia.contains("No hay repuestos registrados."));
        assertTrue(vacia.contains("Ningún repuesto está en su stock mínimo."));

        filtroConStock(1);
        repuestos.registrar(Repuesto.nuevo("Bujía", "NGK", 1, new BigDecimal("12000.00"), 20, 2));
        String salida = ejecutar("7", "0");
        assertTrue(salida.contains("Repuesto 1 | Filtro de aceite") && salida.contains("¡Stock mínimo!"), salida);
        assertFalse(salida.contains("Bujía"));
    }

    @Test
    void unErrorDeBaseDeDatosNoMuestraDetallesInternosYElMenuSigue() {
        RepuestoDAO falla = new RepuestoDAO(() -> {
            throw new SQLException("Access denied for user 'root' (detalle interno)");
        });

        String salida = ejecutar(falla, "3", "2", "1", "0");

        assertEquals(2, salida.split("Error de base de datos. Revise la conexión", -1).length - 1, salida);
        assertFalse(salida.contains("Access denied"));
        assertTrue(salida.trim().endsWith("Opción:"));
    }

    @Test
    void muestraLosTiposAntesDePedirElIdYRechazaUnoQueNoEsta() {
        repuestos.tipos(Map.of(1, "Filtros", 2, "Frenos"));

        String salida = ejecutar("1", "Filtro", "Bosch", "99", "1", "Pastilla", "Brembo", "2", "80000", "4", "1", "0");

        assertTrue(salida.contains("Tipos disponibles: 1 = Filtros, 2 = Frenos"), salida);
        assertTrue(salida.contains("El tipo de repuesto 99 no existe. Elija uno de la lista."));
        assertTrue(salida.contains("Repuesto registrado con ID 1."));
        assertEquals(1, repuestos.cantidadGuardados(), "El intento con el tipo 99 no se guardó");
        assertEquals(2, repuestos.buscarPorId(1).orElseThrow().repuestoTipoId());
    }

    @Test
    void sinTiposCargadosAvisaComoCargarlosYNoPideDatos() {
        repuestos.tipos(Map.of());

        String salida = ejecutar("1", "Filtro", "Bosch", "0");

        assertTrue(salida.contains("No hay tipos de repuesto registrados. Cárguelos con "
                + "docs/inventario/repuesto_tipos_iniciales.sql"), salida);
        assertFalse(salida.contains("ID del tipo de repuesto"), "No pide el tipo ni el resto de datos");
        assertEquals(0, repuestos.cantidadGuardados());
    }

    @Test
    void siLaBaseRechazaElTipoPorUnaCarreraSeMuestraElMotivoSinDetalles() {
        RepuestoDAO sinTipo = new RepuestoDAO(() -> {
            throw new SQLException("No debe abrir conexiones");
        }) {
            @Override
            public Map<Integer, String> listarTipos() {
                return Map.of(1, "Filtros");
            }

            @Override
            public Repuesto registrar(Repuesto repuesto) throws SQLException {
                throw new SQLIntegrityConstraintViolationException("fk_repuestos_tipo");
            }
        };

        String salida = ejecutar(sinTipo, "1", "Filtro", "Bosch", "1", "100", "1", "1", "0");

        assertTrue(salida.contains("verifique que el tipo de repuesto exista"), salida);
        assertFalse(salida.contains("fk_repuestos_tipo"));
    }

    @Test
    void muestraLosRepuestosDeUnaOrdenConLoReservadoYLoUsado() {
        filtroConStock(7);
        ConsultaReservas consulta = servicioId -> servicioId == 3
                ? List.of(new ReservaRepuestoDAO.Linea(3, 1, 1, 2, new BigDecimal("25000.00")))
                : List.of();

        String salida = ejecutar(repuestos, consulta, "8", "3", "8", "9", "8", "x", "0");

        assertTrue(salida.contains("Repuesto 1 | Filtro de aceite | Reservadas sin usar: 1 | Usadas: 2 | Precio unitario: 25000.00"), salida);
        assertTrue(salida.contains("Hay 1 unidades reservadas sin usar"));
        assertTrue(salida.contains("La orden 9 no tiene repuestos reservados ni usados."));
        assertTrue(salida.contains("ID de la orden de servicio debe ser un número entero."));
    }

    @Test
    void unaOrdenSinPendientesNoMuestraElAviso() {
        filtroConStock(8);
        ConsultaReservas consulta = servicioId -> List.of(new ReservaRepuestoDAO.Linea(servicioId, 1, 0, 2, new BigDecimal("25000.00")));

        String salida = ejecutar(repuestos, consulta, "8", "3", "0");

        assertTrue(salida.contains("Reservadas sin usar: 0 | Usadas: 2"), salida);
        assertFalse(salida.contains("unidades reservadas sin usar"));
    }

    @Test
    void siLaEntradaSeAcabaEnMedioDeUnaOperacionVuelveSinGuardar() {
        String salida = ejecutar("1", "Filtro", "Bosch");

        assertTrue(salida.contains("ID del tipo de repuesto: "));
        assertEquals(0, repuestos.cantidadGuardados());
    }

    @Test
    void noCierraLaEntradaCompartidaAlVolverAlMenuPrincipal() {
        boolean[] cerrada = {false};
        var flujo = new FilterInputStream(new ByteArrayInputStream("3\n0\nsiguiente\n".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public void close() throws IOException {
                cerrada[0] = true;
                super.close();
            }
        };
        Scanner compartido = new Scanner(flujo, StandardCharsets.UTF_8);
        PrintStream salida = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        InventarioControlador controlador =
                new InventarioControlador(new InventarioService(repuestos, new ProveedoresEnMemoria()));

        new VistaInventario(compartido, salida, controlador).mostrar();

        assertFalse(cerrada[0], "La vista no debe cerrar System.in");
        assertEquals("siguiente", compartido.nextLine(), "El menú principal puede seguir leyendo");
    }
}
