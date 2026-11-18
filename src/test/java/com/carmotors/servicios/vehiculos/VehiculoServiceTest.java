
package com.carmotors.servicios.vehiculos;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VehiculoServiceTest {

    private final VehiculosEnMemoria vehiculos =
            new VehiculosEnMemoria();

    private final ClienteDAO clientes = new ClienteDAO() {
        @Override
        public Optional<Cliente> buscarPorId(int id) {
            return id == 1
                    ? Optional.of(new Cliente())
                    : Optional.empty();
        }
    };

    private final VehiculoService servicio =
            new VehiculoService(vehiculos, clientes);

    @Test
    void registraVehiculoValido() throws Exception {
        Vehiculo registrado =
                servicio.registrar(nuevoVehiculo("ABC123"));

        assertEquals(1, registrado.getId());
        assertEquals("ABC123", registrado.getPlaca());
        assertEquals(1, servicio.listar().size());
    }

    @Test
    void rechazaPlacaVacia() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(nuevoVehiculo("  ")));
    }

    @Test
    void rechazaPlacaInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(nuevoVehiculo("A!")));
    }

    @Test
    void rechazaPlacaDuplicada() throws Exception {
        servicio.registrar(nuevoVehiculo("ABC123"));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(nuevoVehiculo("ABC123")));
    }

    @Test
    void rechazaClienteInexistente() {
        Vehiculo vehiculo = nuevoVehiculo("XYZ789");
        vehiculo.setClienteId(999);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(vehiculo));
    }

    @Test
    void rechazaAnioInvalido() {
        Vehiculo vehiculo = nuevoVehiculo("XYZ789");
        vehiculo.setAnio(1800);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(vehiculo));
    }

    @Test
    void buscaPorId() throws Exception {
        Vehiculo registrado =
                servicio.registrar(nuevoVehiculo("ABC123"));

        Optional<Vehiculo> encontrado =
                servicio.buscarPorId(registrado.getId());

        assertTrue(encontrado.isPresent());
        assertEquals("ABC123", encontrado.get().getPlaca());
    }

    @Test
    void buscaPorPlaca() throws Exception {
        servicio.registrar(nuevoVehiculo("ABC123"));

        Optional<Vehiculo> encontrado =
                servicio.buscarPorPlaca("abc123");

        assertTrue(encontrado.isPresent());
    }

    @Test
    void listaVehiculos() throws Exception {
        servicio.registrar(nuevoVehiculo("ABC123"));
        servicio.registrar(nuevoVehiculo("XYZ789"));

        assertEquals(2, servicio.listar().size());
    }

    @Test
    void rechazaDependenciasNulas() {
        assertThrows(NullPointerException.class,
                () -> new VehiculoService(null, clientes));

        assertThrows(NullPointerException.class,
                () -> new VehiculoService(vehiculos, null));
    }

    private Vehiculo nuevoVehiculo(String placa) {
        return new Vehiculo(
                1, placa, "Toyota", "Corolla", 2022);
    }

    private static class VehiculosEnMemoria
            extends VehiculoDAO {

        private final List<Vehiculo> registros =
                new ArrayList<>();

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
