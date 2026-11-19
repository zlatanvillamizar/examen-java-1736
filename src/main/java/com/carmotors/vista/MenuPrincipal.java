package com.carmotors.vista;

import com.carmotors.controlador.ClienteControlador;
import com.carmotors.controlador.FacturaControlador;
import com.carmotors.controlador.InventarioControlador;
import com.carmotors.controlador.RecordatorioControlador;
import com.carmotors.controlador.ServicioControlador;
import com.carmotors.controlador.VehiculoControlador;

import java.io.PrintStream;
import java.util.Map;
import java.util.Scanner;

public class MenuPrincipal {
    public void mostrar() {
        Scanner entrada = new Scanner(System.in);
        PrintStream salida = System.out;
        VistaClientes clientes = new VistaClientes(entrada, salida, new ClienteControlador());
        VistaServicios servicios = new VistaServicios(entrada, salida, new ServicioControlador());
        VistaVehiculos vehiculos = new VistaVehiculos(entrada, salida, new VehiculoControlador());
        InventarioControlador inventarioControlador = new InventarioControlador();
        VistaInventario inventario = new VistaInventario(entrada, salida, inventarioControlador);
        VistaProveedores proveedores = new VistaProveedores(entrada, salida, inventarioControlador);
        RecordatorioVista recordatorios = new RecordatorioVista(new RecordatorioControlador(), entrada);
        FacturaVista facturas = new FacturaVista(new FacturaControlador(), entrada);
        mostrar(entrada, salida, Map.of(
                "1", clientes::mostrar, "2", vehiculos::mostrar, "3", servicios::mostrar,
                "4", inventario::mostrar, "5", proveedores::mostrar,
                "6", recordatorios::mostrarMenu, "7", facturas::mostrarMenu));
    }

    // Cada acción usa la entrada compartida y devuelve el control con 0 o al llegar a EOF.
    void mostrar(Scanner entrada, PrintStream salida, Map<String, Runnable> rutas) {
        while (true) {
            salida.println("""
                    === CarMotors SGT ===
                    1. Clientes
                    2. Vehículos
                    3. Servicios y Técnicos
                    4. Inventario y Repuestos
                    5. Proveedores
                    6. Recordatorios y Alertas
                    7. Facturación
                    0. Salir""");
            salida.print("Opción: ");
            if (!entrada.hasNextLine()) {
                return;
            }
            String opcion = entrada.nextLine().strip();
            switch (opcion) {
                case "0" -> { return; }
                case "1", "2", "3", "4", "5", "6", "7" ->
                        abrirVista(rutas.get(opcion), salida);
                default -> salida.println("Opción inválida. Elija un número entre 0 y 7.");
            }
        }
    }

    private static void abrirVista(Runnable vista, PrintStream salida) {
        if (vista == null) {
            salida.println("La vista de este módulo está pendiente de integración.");
        } else {
            vista.run();
        }
    }
}
