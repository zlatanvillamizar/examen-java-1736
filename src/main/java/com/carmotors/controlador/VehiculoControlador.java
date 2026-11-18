
package com.carmotors.controlador;

import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicios.vehiculos.VehiculoService;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Controlador del módulo de Vehículos.
 * Recibe solicitudes y delega en VehiculoService.
 * No contiene consultas SQL.
 */
public final class VehiculoControlador {

    private final VehiculoService servicio;

    public VehiculoControlador() {
        this(new VehiculoService());
    }

    public VehiculoControlador(VehiculoService servicio) {
        this.servicio = Objects.requireNonNull(servicio);
    }

    // Registrar un vehículo
    public Vehiculo registrarVehiculo(Vehiculo solicitud)
            throws SQLException {
        return servicio.registrar(solicitud);
    }

    // Buscar un vehículo por su ID
    public Optional<Vehiculo> buscarVehiculo(int id)
            throws SQLException {
        return servicio.buscarPorId(id);
    }

    // Buscar un vehículo por su placa
    public Optional<Vehiculo> buscarPorPlaca(String placa)
            throws SQLException {
        return servicio.buscarPorPlaca(placa);
    }

    // Listar todos los vehículos
    public List<Vehiculo> listarVehiculos()
            throws SQLException {
        return servicio.listar();
    }
}
