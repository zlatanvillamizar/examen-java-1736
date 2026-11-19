
package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Vehiculo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class VehiculoDAO {

    private static final String INSERT =
            "INSERT INTO vehiculos (cliente_id, placa, marca, modelo, anio) " +
                    "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID =
            "SELECT id, cliente_id, placa, marca, modelo, anio " +
                    "FROM vehiculos WHERE id = ?";

    private static final String SELECT_BY_PLACA =
            "SELECT id, cliente_id, placa, marca, modelo, anio " +
                    "FROM vehiculos WHERE placa = ?";

    private static final String SELECT_ALL =
            "SELECT id, cliente_id, placa, marca, modelo, anio " +
                    "FROM vehiculos ORDER BY id";

    private final FuenteConexion fuente;

    public VehiculoDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public VehiculoDAO(FuenteConexion fuente) {
        this.fuente = Objects.requireNonNull(fuente);
    }

    public Vehiculo registrar(Vehiculo vehiculo) throws SQLException {

        try (Connection conexion = fuente.obtener();
             PreparedStatement ps = conexion.prepareStatement(
                     INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, vehiculo.getClienteId());
            ps.setString(2, vehiculo.getPlaca());
            ps.setString(3, vehiculo.getMarca());
            ps.setString(4, vehiculo.getModelo());
            ps.setInt(5, vehiculo.getAnio());

            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (claves.next()) {
                    vehiculo.setId(claves.getInt(1));
                }
            }

            return vehiculo;
        }
    }

    public Optional<Vehiculo> buscarPorId(int id) throws SQLException {

        try (Connection conexion = fuente.obtener();
             PreparedStatement ps = conexion.prepareStatement(SELECT_BY_ID)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearVehiculo(rs));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Vehiculo> buscarPorPlaca(String placa) throws SQLException {

        try (Connection conexion = fuente.obtener();
             PreparedStatement ps = conexion.prepareStatement(SELECT_BY_PLACA)) {

            ps.setString(1, placa);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearVehiculo(rs));
                }
            }
        }

        return Optional.empty();
    }

    public List<Vehiculo> listar() throws SQLException {

        List<Vehiculo> vehiculos = new ArrayList<>();

        try (Connection conexion = fuente.obtener();
             PreparedStatement ps = conexion.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                vehiculos.add(mapearVehiculo(rs));
            }
        }

        return vehiculos;
    }

    private Vehiculo mapearVehiculo(ResultSet rs) throws SQLException {

        Vehiculo vehiculo = new Vehiculo();

        vehiculo.setId(rs.getInt("id"));
        vehiculo.setClienteId(rs.getInt("cliente_id"));
        vehiculo.setPlaca(rs.getString("placa"));
        vehiculo.setMarca(rs.getString("marca"));
        vehiculo.setModelo(rs.getString("modelo"));
        vehiculo.setAnio(rs.getInt("anio"));

        return vehiculo;
    }
}
