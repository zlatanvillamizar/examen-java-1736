package com.carmotors.vista;

import com.carmotors.controlador.InventarioControlador;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.ProveedoresEnMemoria;
import com.carmotors.dao.RepuestosEnMemoria;
import com.carmotors.modelo.Proveedor;
import com.carmotors.servicios.inventario.InventarioService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vista de consola de Proveedores con DAO en memoria (sin MySQL). */
class VistaProveedoresTest {

    private final ProveedoresEnMemoria proveedores = new ProveedoresEnMemoria();

    private String ejecutar(ProveedorDAO dao, String... lineas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            InventarioControlador controlador =
                    new InventarioControlador(new InventarioService(new RepuestosEnMemoria(), dao));
            new VistaProveedores(new Scanner(String.join("\n", lineas)), salida, controlador).mostrar();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String ejecutar(String... lineas) {
        return ejecutar(proveedores, lineas);
    }

    @Test
    void registraBuscaYListaProveedores() {
        String salida = ejecutar(
                "1", "AutoPartes SAS", "Laura Gómez", "3001234567", "ventas@autopartes.co",
                "1", "Frenos del Norte", "", "", "",
                "2", "1", "3", "frenos del norte", "4", "0");

        assertTrue(salida.contains("Proveedor registrado con ID 1."), salida);
        assertTrue(salida.contains("Proveedor registrado con ID 2."));
        assertTrue(salida.contains(
                "Proveedor 1 | AutoPartes SAS | Contacto: Laura Gómez | Teléfono: 3001234567 | Email: ventas@autopartes.co"));
        assertTrue(salida.contains(
                "Proveedor 2 | Frenos del Norte | Contacto: sin registrar | Teléfono: sin registrar | Email: sin registrar"));
        assertEquals(2, proveedores.cantidadGuardados());
    }

    @Test
    void actualizaSoloLosDatosDeContactoConservandoBorrandoYReemplazando() {
        proveedores.registrar(Proveedor.nuevo("AutoPartes SAS", "Laura", "3001234567", "ventas@ap.co"));

        String salida = ejecutar("5", "1", "", "-", "compras@ap.co", "0");

        Proveedor actualizado = proveedores.buscarPorId(1).orElseThrow();
        assertEquals("AutoPartes SAS", actualizado.nombre(), "El nombre no se modifica");
        assertEquals("Laura", actualizado.personaContacto(), "Enter conserva el valor");
        assertNull(actualizado.telefono(), "Un guion borra el valor");
        assertEquals("compras@ap.co", actualizado.email());
        assertTrue(salida.contains("Contacto del proveedor actualizado."), salida);
        assertTrue(salida.contains("Persona de contacto [Laura] (Enter conserva, - borra): "));
    }

    @Test
    void actualizarUnProveedorInexistenteNoPideDatos() {
        String salida = ejecutar("5", "42", "0");

        assertTrue(salida.contains("No se encontró el proveedor 42."), salida);
        assertFalse(salida.contains("Persona de contacto"));
    }

    @Test
    void validaEntradasYReglasSinSalirDelMenu() {
        proveedores.registrar(Proveedor.nuevo("AutoPartes SAS", null, null, null));

        String salida = ejecutar("9", "2", "uno", "2", "-4",
                "1", "",
                "1", "AutoPartes SAS", "", "", "",
                "1", "Nuevo", "", "", "correo-invalido",
                "5", "1", "", "", "sin-arroba",
                "3", " ", "3", "Inexistente", "2", "77",
                "4", "0");

        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 5."), salida);
        assertTrue(salida.contains("ID del proveedor debe ser un número entero."));
        assertTrue(salida.contains("ID del proveedor debe ser positivo."));
        assertTrue(salida.contains("Nombre es obligatorio."));
        assertTrue(salida.contains("Error: Ya existe un proveedor con el nombre AutoPartes SAS"));
        assertEquals(2, salida.split("Validación: El email del proveedor no es válido", -1).length - 1);
        assertTrue(salida.contains("Nombre del proveedor es obligatorio."));
        assertTrue(salida.contains("No se encontró un proveedor con el nombre Inexistente."));
        assertTrue(salida.contains("No se encontró el proveedor 77."));
        assertTrue(salida.lastIndexOf("Proveedor 1 | AutoPartes SAS") > salida.lastIndexOf("Validación"));
        assertEquals(1, proveedores.cantidadGuardados());
        assertNull(proveedores.buscarPorId(1).orElseThrow().email());
    }

    @Test
    void listaVaciaYErroresDeBaseDeDatos() {
        assertTrue(ejecutar("4", "0").contains("No hay proveedores registrados."));

        ProveedorDAO falla = new ProveedorDAO(() -> {
            throw new SQLException("Communications link failure (detalle interno)");
        });
        String salida = ejecutar(falla, "4", "0");
        assertTrue(salida.contains("Error de base de datos. Revise la conexión"), salida);
        assertFalse(salida.contains("Communications link failure"));
    }

    @Test
    void unNombreDuplicadoRechazadoPorLaBaseMuestraElMotivo() {
        ProveedorDAO carrera = new ProveedorDAO(() -> {
            throw new AssertionError("No debe abrir conexiones");
        }) {
            @Override
            public Optional<Proveedor> buscarPorNombre(String nombre) {
                return Optional.empty();
            }

            @Override
            public Proveedor registrar(Proveedor proveedor) throws SQLException {
                throw new SQLIntegrityConstraintViolationException("uq_proveedores_nombre");
            }
        };

        String salida = ejecutar(carrera, "1", "AutoPartes SAS", "", "", "", "0");

        assertTrue(salida.contains("ya existe uno con ese nombre"), salida);
        assertFalse(salida.contains("uq_proveedores_nombre"));
    }

    @Test
    void siLaEntradaSeAcabaVuelveSinGuardar() {
        String salida = ejecutar("1", "AutoPartes SAS", "Laura");

        assertTrue(salida.contains("Teléfono (Enter para omitir): "));
        assertEquals(0, proveedores.cantidadGuardados());
    }

    @Test
    void ambasVistasPuedenUsarElMismoScannerComoEnElMenuPrincipal() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream salida = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        Scanner compartido = new Scanner(String.join("\n",
                "1", "AutoPartes SAS", "", "", "", "0",
                "3", "0",
                "4", "0"));
        InventarioControlador controlador =
                new InventarioControlador(new InventarioService(new RepuestosEnMemoria(), proveedores));
        VistaInventario inventario = new VistaInventario(compartido, salida, controlador);
        VistaProveedores vistaProveedores = new VistaProveedores(compartido, salida, controlador);

        vistaProveedores.mostrar();
        inventario.mostrar();
        vistaProveedores.mostrar();

        String texto = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("Proveedor registrado con ID 1."), texto);
        assertTrue(texto.contains("No hay repuestos registrados."));
        assertTrue(texto.contains("Proveedor 1 | AutoPartes SAS"));
        assertFalse(compartido.hasNextLine(), "Se consumió exactamente la entrada de cada vista");
    }
}
