package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;
import com.carmotors.controlador.VehiculoControlador;

import java.util.Scanner;

public class MenuPrincipal {
    public void mostrar() {
        Scanner entrada = new Scanner(System.in);
        VistaServicios servicios = new VistaServicios(entrada, System.out, new ServicioControlador());
        VistaVehiculos vehiculos = new VistaVehiculos(entrada, System.out, new VehiculoControlador());
        while (true) {
            System.out.println("""
                    === CarMotors SGT ===
                    1. Clientes
                    2. Vehículos
                    3. Servicios y Técnicos
                    4. Inventario y Repuestos
                    5. Proveedores
                    6. Recordatorios y Alertas
                    7. Facturación
                    0. Salir""");
            System.out.print("Opción: ");
            if (!entrada.hasNextLine()) {
                return;
            }
            switch (entrada.nextLine().strip()) {
                case "0" -> { return; }
                case "2" -> vehiculos.mostrar();
                case "3" -> servicios.mostrar();
                case "1", "4", "5", "6", "7" ->
                        System.out.println("La vista de este módulo está pendiente de integración.");
                default -> System.out.println("Opción inválida. Elija un número entre 0 y 7.");
            }
        }
    }
}
