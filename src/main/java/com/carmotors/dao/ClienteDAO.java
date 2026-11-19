package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ClienteDAO {

    private static final String INSERT =
            "INSERT INTO clientes (nombre, documento_identidad, telefono, email) VALUES (?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT * FROM clientes WHERE id = ?";
    private static final String SELECT_BY_DOC =
            "SELECT * FROM clientes WHERE documento_identidad = ?";
    private static final String SELECT_ALL =
            "SELECT * FROM clientes ORDER BY nombre";
    private static final String UPDATE_CONTACTO =
            "UPDATE clientes SET telefono = ?, email = ? WHERE id = ?";

    private final FuenteConexion fuente;

    public ClienteDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public ClienteDAO(FuenteConexion fuente) {
        this.fuente = Objects.requireNonNull(fuente);
    }

    public Cliente registrar(Cliente c) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getDocumentoIdentidad());
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getEmail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getInt(1));
            }
            return c;
        }
    }

    public Optional<Cliente> buscarPorId(int id) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Cliente> buscarPorDocumento(String documento) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_DOC)) {
            ps.setString(1, documento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    public List<Cliente> listar() throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    public boolean actualizarContacto(int id, String telefono, String email) throws SQLException {
        try (Connection con = fuente.obtener();
             PreparedStatement ps = con.prepareStatement(UPDATE_CONTACTO)) {
            ps.setString(1, telefono);
            ps.setString(2, email);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getInt("id"));
        c.setNombre(rs.getString("nombre"));
        c.setDocumentoIdentidad(rs.getString("documento_identidad"));
        c.setTelefono(rs.getString("telefono"));
        c.setEmail(rs.getString("email"));
        return c;
    }
}
