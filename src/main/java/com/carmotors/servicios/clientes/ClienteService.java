package com.carmotors.servicios.clientes;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.modelo.Cliente;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ClienteService {

    private static final int MAX_NOMBRE = 255;
    private static final int MAX_DOCUMENTO = 20;
    private static final int MAX_TELEFONO = 20;
    private static final int MAX_EMAIL = 100;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ClienteDAO clienteDAO;

    public ClienteService() {
        this(new ClienteDAO());
    }

    public ClienteService(ClienteDAO clienteDAO) {
        if (clienteDAO == null) {
            throw new IllegalArgumentException("El DAO es obligatorio");
        }
        this.clienteDAO = clienteDAO;
    }

    public Cliente registrar(String nombre, String documento, String telefono, String email)
            throws SQLException {
        String nombreN = textoObligatorio(nombre, "El nombre", MAX_NOMBRE);
        String documentoN = textoObligatorio(documento, "El documento", MAX_DOCUMENTO);
        String telefonoN = telefonoOpcional(telefono);
        String emailN = emailValido(email);

        if (clienteDAO.buscarPorDocumento(documentoN).isPresent()) {
            throw new IllegalStateException("Ya existe un cliente con el documento " + documentoN);
        }
        return clienteDAO.registrar(new Cliente(nombreN, documentoN, telefonoN, emailN));
    }

    public Optional<Cliente> buscarPorId(int id) throws SQLException {
        validarId(id);
        return clienteDAO.buscarPorId(id);
    }

    public Optional<Cliente> buscarPorDocumento(String documento) throws SQLException {
        String documentoN = textoObligatorio(documento, "El documento", MAX_DOCUMENTO);
        return clienteDAO.buscarPorDocumento(documentoN);
    }

    public List<Cliente> listar() throws SQLException {
        return clienteDAO.listar();
    }

    public boolean actualizarContacto(int id, String telefono, String email) throws SQLException {
        validarId(id);
        String telefonoN = telefonoOpcional(telefono);
        String emailN = emailValido(email);
        return clienteDAO.actualizarContacto(id, telefonoN, emailN);
    }

    // ---------- validaciones privadas ----------

    private void validarId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser un número positivo");
        }
    }

    private String textoObligatorio(String valor, String campo, int max) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        String limpio = valor.trim();
        if (limpio.length() > max) {
            throw new IllegalArgumentException(campo + " no puede superar " + max + " caracteres");
        }
        return limpio;
    }

    private String telefonoOpcional(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) {
            return null;
        }
        String limpio = telefono.trim();
        if (limpio.length() > MAX_TELEFONO) {
            throw new IllegalArgumentException("El teléfono no puede superar " + MAX_TELEFONO + " caracteres");
        }
        return limpio;
    }

    private String emailValido(String email) {
        String limpio = textoObligatorio(email, "El email", MAX_EMAIL).toLowerCase();
        if (!EMAIL_PATTERN.matcher(limpio).matches()) {
            throw new IllegalArgumentException("El email no tiene un formato válido");
        }
        return limpio;
    }
}