package com.carmotors.vista;

import com.carmotors.controlador.RecordatorioControlador;
import com.carmotors.modelo.Recordatorio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class RecordatorioVistaTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "3\n", "8\n1\n", "1\n1\n", "1\n1\n\nfecha\n", "1\n1\n\n2099-01-01\n"})
    void terminaAlAgotarEntrada(String entrada) {
        try (Scanner scanner = new Scanner(entrada)) {
            assertDoesNotThrow(() -> new RecordatorioVista(new RecordatorioControlador(), scanner)
                    .mostrarMenu());
            assertFalse(scanner.hasNextLine());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0\n", "\ntexto\n2147483648\n-1\n3\n-1\n0\n"})
    void ceroDevuelveElControlYConservaElScanner(String entrada) {
        RecordatorioControlador controlador = new RecordatorioControlador() {
            @Override
            public List<Recordatorio> listarProximos(int dias) {
                assertEquals(-1, dias);
                throw new IllegalArgumentException("Días inválidos");
            }
        };
        try (Scanner scanner = new Scanner(entrada + "siguiente\n")) {
            new RecordatorioVista(controlador, scanner).mostrarMenu();
            assertEquals("siguiente", scanner.nextLine());
        }
    }
}
