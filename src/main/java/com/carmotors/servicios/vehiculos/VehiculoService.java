
package com.carmotors.servicios.vehiculos;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.modelo.Vehiculo;

import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class VehiculoService {

    private final VehiculoDAO vehiculoDAO;
    private final ClienteDAO clienteDAO;

    public VehiculoService() {
        this(new VehiculoDAO(), new ClienteDAO());
    }

    // Validar que las dependencias no sean nulas
    public VehiculoService(VehiculoDAO vehiculoDAO,
                           ClienteDAO clienteDAO) {

        this.vehiculoDAO = Objects.requireNonNull(
                vehiculoDAO,
                "El VehiculoDAO no puede ser nulo"
        );

        this.clienteDAO = Objects.requireNonNull(
                clienteDAO,
                "El ClienteDAO no puede ser nulo"
        );
    }

    // Registrar un vehículo
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

        placa = placa.trim().toUpperCase(Locale.ROOT);

        if (!placa.matches("[A-Z0-9]{5,10}")) {
            throw new IllegalArgumentException(
                    "La placa debe tener entre 5 y 10 caracteres alfanuméricos");
        }

        // Validar placas duplicadas
        if (vehiculoDAO.buscarPorPlaca(placa).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un vehículo con esa placa");
        }

        // Validar marca
        if (vehiculo.getMarca() == null ||
                vehiculo.getMarca().isBlank()) {
            throw new IllegalArgumentException(
                    "La marca es obligatoria");
        }
        if (vehiculo.getMarca().codePointCount(0, vehiculo.getMarca().length()) > 50) {
            throw new IllegalArgumentException(
                    "La marca debe tener como máximo 50 caracteres");
        }

        // Validar modelo
        if (vehiculo.getModelo() == null ||
                vehiculo.getModelo().isBlank()) {
            throw new IllegalArgumentException(
                    "El modelo es obligatorio");
        }
        if (vehiculo.getModelo().codePointCount(0, vehiculo.getModelo().length()) > 50) {
            throw new IllegalArgumentException(
                    "El modelo debe tener como máximo 50 caracteres");
        }

        // Validar año
        int anioActual = Year.now().getValue();

        if (vehiculo.getAnio() < 1900 ||
                vehiculo.getAnio() > anioActual + 1) {
            throw new IllegalArgumentException(
                    "El año del vehículo no es válido");
        }

        // Normalizar placa antes de guardar
        vehiculo.setPlaca(placa);

        return vehiculoDAO.registrar(vehiculo);
    }

    // Buscar vehículo por ID
    public Optional<Vehiculo> buscarPorId(int id)
            throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "El ID debe ser positivo");
        }

        return vehiculoDAO.buscarPorId(id);
    }

    // Buscar vehículo por placa
    public Optional<Vehiculo> buscarPorPlaca(String placa)
            throws SQLException {

        if (placa == null || placa.isBlank()) {
            throw new IllegalArgumentException(
                    "La placa es obligatoria");
        }

        return vehiculoDAO.buscarPorPlaca(
                placa.trim().toUpperCase(Locale.ROOT));
    }

    // Listar todos los vehículos
    public List<Vehiculo> listar() throws SQLException {
        return vehiculoDAO.listar();
    }
}
