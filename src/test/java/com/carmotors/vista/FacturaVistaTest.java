package com.carmotors.vista;

import com.carmotors.controlador.FacturaControlador;
import com.carmotors.modelo.Factura;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class FacturaVistaTest {
    private final FacturaControlador controlador = new FacturaControlador() {
        @Override
        public Factura crear(int id) {
            return fail("La emisión incompleta no debe llegar al controlador");
        }

        @Override
        public Optional<Factura> buscarPorId(int id) {
            return fail("La consulta incompleta no debe llegar al controlador");
        }
    };

    @ParameterizedTest
    @ValueSource(strings = {"", "1\n", "2\n", "1\ntexto\n"})
    void vuelveAlAgotarEntrada(String entrada) {
        try (Scanner scanner = new Scanner(entrada)) {
            assertDoesNotThrow(() -> new FacturaVista(controlador, scanner).mostrarMenu());
            assertFalse(scanner.hasNextLine());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0\n", "\ntexto\n2147483648\n-1\n0\n"})
    void ceroConservaElScannerCompartido(String entrada) {
        try (Scanner scanner = new Scanner(entrada + "principal\n")) {
            new FacturaVista(controlador, scanner).mostrarMenu();
            assertEquals("principal", scanner.nextLine());
        }
    }
}
