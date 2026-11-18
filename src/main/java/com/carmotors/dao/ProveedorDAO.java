package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.modelo.Proveedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Acceso a datos de la tabla {@code proveedores} con JDBC y {@link PreparedStatement}. */
public class ProveedorDAO {

    private static final String COLUMNAS = "id, nombre, persona_contacto, telefono, email";

    private static final String SQL_LISTAR =
            "SELECT " + COLUMNAS + " FROM proveedores ORDER BY id";
    private static final String SQL_BUSCAR_POR_ID =
            "SELECT " + COLUMNAS + " FROM proveedores WHERE id = ?";
    private static final String SQL_BUSCAR_POR_NOMBRE =
            "SELECT " + COLUMNAS + " FROM proveedores WHERE nombre = ?";
    private static final String SQL_INSERTAR =
            "INSERT INTO proveedores (nombre, persona_contacto, telefono, email) VALUES (?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR_CONTACTO =
            "UPDATE proveedores SET persona_contacto = ?, telefono = ?, email = ? WHERE id = ?";

    private final FuenteConexion fuente;

    /** Usa la conexión configurada en {@link ConexionFactory}. */
    public ProveedorDAO() {
        this(ConexionFactory::obtenerConexion);
    }

    public ProveedorDAO(FuenteConexion fuente) {
        this.fuente = Objects.requireNonNull(fuente);
    }

    /** Devuelve todos los proveedores ordenados por id, o una lista vacía. */
    public List<Proveedor> listar() throws SQLException {
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_LISTAR);
             ResultSet filas = sentencia.executeQuery()) {
            List<Proveedor> proveedores = new ArrayList<>();
            while (filas.next()) {
                proveedores.add(mapear(filas));
            }
            return proveedores;
        }
    }

    /** Devuelve un Optional vacío cuando el proveedor no existe. */
    public Optional<Proveedor> buscarPorId(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("El id del proveedor debe ser positivo");
        }
        return buscarUno(SQL_BUSCAR_POR_ID, sentencia -> sentencia.setInt(1, id));
    }

    /** Busca por nombre exacto (la columna es UNIQUE). */
    public Optional<Proveedor> buscarPorNombre(String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
        String limpio = nombre.strip();
        return buscarUno(SQL_BUSCAR_POR_NOMBRE, sentencia -> sentencia.setString(1, limpio));
    }

    /**
     * Guarda un proveedor nuevo (sin id) y lo devuelve con el id asignado.
     *
     * @throws IllegalArgumentException si el proveedor ya trae id
     */
    public Proveedor registrar(Proveedor proveedor) throws SQLException {
        Objects.requireNonNull(proveedor, "El proveedor es obligatorio");
        if (proveedor.id() != null) {
            throw new IllegalArgumentException("Un proveedor nuevo no debe traer id");
        }
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia =
                     conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, proveedor.nombre());
            sentencia.setString(2, proveedor.personaContacto());
            sentencia.setString(3, proveedor.telefono());
            sentencia.setString(4, proveedor.email());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("MySQL no devolvió el id del proveedor insertado");
                }
                return proveedor.conId(claves.getInt(1));
            }
        }
    }

    /**
     * Actualiza los datos de contacto del proveedor ({@code id} obligatorio).
     *
     * @return {@code false} si el proveedor no existe
     */
    public boolean actualizarContacto(Proveedor proveedor) throws SQLException {
        Objects.requireNonNull(proveedor, "El proveedor es obligatorio");
        if (proveedor.id() == null) {
            throw new IllegalArgumentException("Para actualizar, el proveedor debe traer id");
        }
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR_CONTACTO)) {
            sentencia.setString(1, proveedor.personaContacto());
            sentencia.setString(2, proveedor.telefono());
            sentencia.setString(3, proveedor.email());
            sentencia.setInt(4, proveedor.id());
            return sentencia.executeUpdate() != 0;
        }
    }

    @FunctionalInterface
    private interface Parametros {
        void aplicar(PreparedStatement sentencia) throws SQLException;
    }

    private Optional<Proveedor> buscarUno(String sql, Parametros parametros) throws SQLException {
        try (Connection conexion = fuente.obtener();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros.aplicar(sentencia);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(mapear(filas)) : Optional.empty();
            }
        }
    }

    private static Proveedor mapear(ResultSet fila) throws SQLException {
        return new Proveedor(
                fila.getInt("id"),
                fila.getString("nombre"),
                fila.getString("persona_contacto"),
                fila.getString("telefono"),
                fila.getString("email"));
    }
}
