
package com.carmotors.controlador;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.vehiculos.VehiculoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VehiculoControladorTest {

    private final VehiculosEnMemoria vehiculos = new VehiculosEnMemoria();

    private final ClienteDAO clientes = new ClienteDAO() {
        @Override
        public Optional<Cliente> buscarPorId(int id) {
            return id == 1 ? Optional.of(new Cliente()) : Optional.empty();
        }
    };

    private final VehiculoControlador controlador =
            new VehiculoControlador(new VehiculoService(vehiculos, clientes));

    @Test
    void registraVehiculoValido() throws Exception {
        Vehiculo registrado = controlador.registrarVehiculo(
                new Vehiculo(1, "ABC123", "Toyota", "Corolla", 2022));

        assertEquals(1, registrado.getId());
        assertEquals("ABC123", registrado.getPlaca());
    }

    @Test
    void rechazaPlacaVacia() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(
                        new Vehiculo(1, "", "Toyota", "Corolla", 2022)));
    }

    @Test
    void rechazaPlacaDuplicada() throws Exception {
        controlador.registrarVehiculo(
                new Vehiculo(1, "ABC123", "Toyota", "Corolla", 2022));

        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(
                        new Vehiculo(1, "ABC123", "Mazda", "3", 2023)));
    }

    @Test
    void rechazaClienteInexistente() {
        assertThrows(IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(
                        new Vehiculo(999, "XYZ789", "Toyota", "Corolla", 2022)));
    }

    @Test
    void buscaVehiculoPorId() throws Exception {
        Vehiculo registrado = controlador.registrarVehiculo(
                new Vehiculo(1, "ABC123", "Toyota", "Corolla", 2022));

        Optional<Vehiculo> encontrado =
                controlador.buscarVehiculo(registrado.getId());

        assertTrue(encontrado.isPresent());
        assertEquals("ABC123", encontrado.get().getPlaca());
    }

    @Test
    void buscaVehiculoPorPlaca() throws Exception {
        controlador.registrarVehiculo(
                new Vehiculo(1, "ABC123", "Toyota", "Corolla", 2022));

        Optional<Vehiculo> encontrado =
                controlador.buscarPorPlaca("abc123");

        assertTrue(encontrado.isPresent());
    }

    @Test
    void listaVehiculos() throws Exception {
        controlador.registrarVehiculo(
                new Vehiculo(1, "ABC123", "Toyota", "Corolla", 2022));

        controlador.registrarVehiculo(
                new Vehiculo(1, "XYZ789", "Mazda", "3", 2023));

        assertEquals(2, controlador.listarVehiculos().size());
    }

    @Test
    void rechazaServicioNulo() {
        assertThrows(NullPointerException.class,
                () -> new VehiculoControlador(null));
    }

    private static class VehiculosEnMemoria extends VehiculoDAO {

        private final List<Vehiculo> registros = new ArrayList<>();

        @Override
        public Vehiculo registrar(Vehiculo vehiculo) {
            vehiculo.setId(registros.size() + 1);
            registros.add(vehiculo);
            return vehiculo;
        }

        @Override
        public Optional<Vehiculo> buscarPorId(int id) {
            return registros.stream()
                    .filter(v -> v.getId() == id)
                    .findFirst();
        }

        @Override
        public Optional<Vehiculo> buscarPorPlaca(String placa) {
            return registros.stream()
                    .filter(v -> v.getPlaca().equals(placa))
                    .findFirst();
        }

        @Override
        public List<Vehiculo> listar() {
            return new ArrayList<>(registros);
        }
    }
}
