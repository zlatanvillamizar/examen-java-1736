package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Tecnico;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TecnicoDAO {
    private static final String CONSULTA = "SELECT id, nombre, especialidad, activo FROM tecnicos";

    public int registrar(Tecnico tecnico) throws SQLException {
        String sql = "INSERT INTO tecnicos (nombre, especialidad, activo) VALUES (?, ?, ?)";
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, tecnico.nombre());
            sentencia.setString(2, tecnico.especialidad());
            sentencia.setBoolean(3, tecnico.activo());
            sentencia.executeUpdate();
            try (var claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("MySQL no devolvió el ID del técnico");
                }
                return claves.getInt(1);
            }
        }
    }

    public Optional<Tecnico> buscarPorId(int id) throws SQLException {
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(CONSULTA + " WHERE id = ?")) {
            sentencia.setInt(1, id);
            try (var filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leer(filas)) : Optional.empty();
            }
        }
    }

    public List<Tecnico> listar() throws SQLException {
        return listarConConsulta(CONSULTA + " ORDER BY id");
    }

    public List<Tecnico> listarActivos() throws SQLException {
        return listarConConsulta(CONSULTA + " WHERE activo = TRUE ORDER BY id");
    }

    public boolean actualizarActivo(int id, boolean activo) throws SQLException {
        String sql = "UPDATE tecnicos SET activo = ? WHERE id = ?";
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql)) {
            sentencia.setBoolean(1, activo);
            sentencia.setInt(2, id);
            return sentencia.executeUpdate() != 0;
        }
    }

    private List<Tecnico> listarConConsulta(String sql) throws SQLException {
        try (var conexion = ConexionFactory.obtenerConexion();
             var sentencia = conexion.prepareStatement(sql);
             var filas = sentencia.executeQuery()) {
            List<Tecnico> tecnicos = new ArrayList<>();
            while (filas.next()) {
                tecnicos.add(leer(filas));
            }
            return tecnicos;
        }
    }

    private Tecnico leer(ResultSet fila) throws SQLException {
        return new Tecnico(fila.getInt("id"), fila.getString("nombre"),
                fila.getString("especialidad"), fila.getBoolean("activo"));
    }
}
