
package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;
import com.carmotors.controlador.VehiculoControlador;

import java.util.Scanner;

public class MenuPrincipal {

    public void mostrar() {
        try (Scanner entrada = new Scanner(System.in)) {

            VistaServicios servicios = new VistaServicios(
                    entrada, System.out, new ServicioControlador());

            VistaVehiculos vehiculos = new VistaVehiculos(
                    entrada, System.out, new VehiculoControlador());

            while (true) {
                System.out.println("""
                        === CarMotors SGT ===
                        1. Servicios
                        2. Vehículos
                        0. Salir""");

                System.out.print("Opción: ");

                if (!entrada.hasNextLine()) {
                    return;
                }

                switch (entrada.nextLine().strip()) {
                    case "0" -> {
                        return;
                    }
                    case "1" -> servicios.mostrar();
                    case "2" -> vehiculos.mostrar();
                    default -> System.out.println(
                            "Opción inválida. Elija 1, 2 o 0.");
                }
            }
        }
    }
}
