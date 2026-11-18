
package com.carmotors.servicios.vehiculos;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Vehiculo;

import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import java.util.Optional;

public class VehiculoService {

    private final VehiculoDAO vehiculoDAO;
    private final ClienteDAO clienteDAO;

    public VehiculoService() {
        this(new VehiculoDAO(), new ClienteDAO());
    }

    public VehiculoService(VehiculoDAO vehiculoDAO,
                           ClienteDAO clienteDAO) {
        this.vehiculoDAO = vehiculoDAO;
        this.clienteDAO = clienteDAO;
    }

    public Vehiculo registrar(Vehiculo vehiculo) throws SQLException {

        if (vehiculo == null) {
            throw new IllegalArgumentException(
                    "El vehículo no puede ser nulo");
        }

        // Validar cliente
        if (vehiculo.getClienteId() <= 0 ||
                clienteDAO.buscarPorId(vehiculo.getClienteId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "El cliente no existe");
        }

        // Validar placa
        String placa = vehiculo.getPlaca();

        if (placa == null || placa.isBlank()) {
            throw new IllegalArgumentException(
                    "La placa es obligatoria");
        }

        placa = placa.trim().toUpperCase();

        if (!placa.matches("[A-Z0-9]{5,10}")) {
            throw new IllegalArgumentException(
                    "La placa debe tener entre 5 y 10 caracteres alfanuméricos");
        }

        // Validar duplicados
        if (vehiculoDAO.buscarPorPlaca(placa).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un vehículo con esa placa");
        }

        // Validar marca y modelo
        if (vehiculo.getMarca() == null ||
                vehiculo.getMarca().isBlank()) {
            throw new IllegalArgumentException(
                    "La marca es obligatoria");
        }

        if (vehiculo.getModelo() == null ||
                vehiculo.getModelo().isBlank()) {
            throw new IllegalArgumentException(
                    "El modelo es obligatorio");
        }

        // Validar año
        int anioActual = Year.now().getValue();

        if (vehiculo.getAnio() < 1900 ||
                vehiculo.getAnio() > anioActual + 1) {
            throw new IllegalArgumentException(
                    "El año del vehículo no es válido");
        }

        vehiculo.setPlaca(placa);

        return vehiculoDAO.registrar(vehiculo);
    }

    public Optional<Vehiculo> buscarPorId(int id)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "El ID debe ser positivo");
        }

        return vehiculoDAO.buscarPorId(id);
    }

    public Optional<Vehiculo> buscarPorPlaca(String placa)
            throws SQLException {

        if (placa == null || placa.isBlank()) {
            throw new IllegalArgumentException(
                    "La placa es obligatoria");
        }

        return vehiculoDAO.buscarPorPlaca(
                placa.trim().toUpperCase());
    }

    public List<Vehiculo> listar() throws SQLException {
        return vehiculoDAO.listar();
    }
}
