package com.carmotors.vista;

import com.carmotors.controlador.VehiculoControlador;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.vehiculos.VehiculoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class VistaVehiculosTest {
    private Vehiculo registrado;

    private final VehiculoDAO dao = new VehiculoDAO() {
        @Override
        public Vehiculo registrar(Vehiculo vehiculo) {
            vehiculo.setId(7);
            registrado = vehiculo;
            return vehiculo;
        }

        @Override
        public Optional<Vehiculo> buscarPorId(int id) throws SQLException {
            if (id == 99) {
                throw new SQLException("detalle interno");
            }
            return Optional.ofNullable(registrado).filter(v -> v.getId() == id);
        }

        @Override
        public Optional<Vehiculo> buscarPorPlaca(String placa) {
            return Optional.ofNullable(registrado).filter(v -> v.getPlaca().equals(placa));
        }

        @Override
        public List<Vehiculo> listar() {
            return registrado == null ? List.of() : List.of(registrado);
        }
    };

    private final VehiculoControlador controlador = new VehiculoControlador(new VehiculoService(
            dao, new ClienteDAO() {
                @Override
                public Optional<Cliente> buscarPorId(int id) {
                    return id == 3 ? Optional.of(new Cliente()) : Optional.empty();
                }
            }));

    @Test
    void registraConsultaListaYConservaEntradaParaElMenuPrincipal() {
        try (Scanner entrada = new Scanner("1\n3\nabc123\nMazda\n3\n2020\n2\n7\n3\nabc123\n4\n0\n1\n")) {
            String salida = mostrar(entrada);
            assertTrue(salida.contains("Vehículo registrado con ID 7."));
            assertEquals(3L, salida.lines().filter(linea ->
                    linea.contains("Vehículo 7 | Cliente 3 | Placa ABC123 | Mazda 3 | Año 2020")).count());
            assertEquals("1", entrada.nextLine());
        }
    }

    @Test
    void muestraListaVaciaYConsultasSinResultado() {
        try (Scanner entrada = new Scanner("4\n2\n7\n3\nABC123\n0\n")) {
            String salida = mostrar(entrada);
            assertTrue(salida.contains("No hay vehículos registrados."));
            assertTrue(salida.contains("No se encontró el vehículo 7."));
            assertTrue(salida.contains("No se encontró un vehículo con esa placa."));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            x|Opción inválida
            2,abc|debe ser un número entero
            2,-1|debe ser positivo
            2,0|debe ser positivo
            1,3, |Placa es obligatorio
            1,3,ABC123, |Marca es obligatorio
            1,3,ABC123,Mazda, |Modelo es obligatorio
            1,3,ABC123,Mazda,3,abc|Año debe ser un número entero
            1,3,ABC123,Mazda,3,1800|El año del vehículo no es válido
            """)
    void rechazaEntradasInvalidasYPermiteContinuar(String datos, String mensaje) {
        try (Scanner entrada = new Scanner(datos.replace(',', '\n') + "\n4\n0\n")) {
            String salida = mostrar(entrada);
            assertTrue(salida.contains(mensaje), salida);
            assertTrue(salida.contains("No hay vehículos registrados."), salida);
            assertNull(registrado);
        }
    }

    @Test
    void validaLasLongitudesDelEsquemaAntesDeRegistrar() {
        String textoLargo = "á".repeat(51);
        String datos = "1\n3\nABC123\n" + textoLargo + "\n3\n2020\n"
                + "1\n3\nABC123\nMazda\n" + textoLargo + "\n2020\n0\n";
        try (Scanner entrada = new Scanner(datos)) {
            String salida = mostrar(entrada);
            assertTrue(salida.contains("La marca debe tener como máximo 50 caracteres"));
            assertTrue(salida.contains("El modelo debe tener como máximo 50 caracteres"));
            assertNull(registrado);
        }
    }

    @Test
    void informaErrorJdbcYContinua() {
        try (Scanner entrada = new Scanner("2\n99\n4\n0\n")) {
            String salida = mostrar(entrada);
            assertTrue(salida.contains("Error de base de datos."));
            assertFalse(salida.contains("detalle interno"));
            assertTrue(salida.contains("No hay vehículos registrados."));
        }
    }

    @Test
    void terminaSiLaEntradaAcabaDuranteElRegistro() {
        try (Scanner entrada = new Scanner("1\n3\nABC123\n")) {
            assertTrue(mostrar(entrada).contains("Marca: "));
            assertNull(registrado);
            assertFalse(entrada.hasNextLine());
        }
    }

    private String mostrar(Scanner entrada) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PrintStream impresion = new PrintStream(salida, true, StandardCharsets.UTF_8)) {
            new VistaVehiculos(entrada, impresion, controlador).mostrar();
        }
        return salida.toString(StandardCharsets.UTF_8);
    }
}
