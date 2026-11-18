package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;

import java.util.Scanner;

public class MenuPrincipal {
    public void mostrar() {
        try (Scanner entrada = new Scanner(System.in)) {
            VistaServicios servicios = new VistaServicios(entrada, System.out, new ServicioControlador());
            while (true) {
                System.out.println("""
                        === CarMotors SGT ===
                        1. Servicios
                        0. Salir""");
                System.out.print("Opción: ");
                if (!entrada.hasNextLine()) {
                    return;
                }
                switch (entrada.nextLine().strip()) {
                    case "0" -> { return; }
                    case "1" -> servicios.mostrar();
                    default -> System.out.println("Opción inválida. Elija 1 o 0.");
                }
            }
        }
    }
}
