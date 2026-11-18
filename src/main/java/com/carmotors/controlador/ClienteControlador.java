package com.carmotors.controlador;

import com.carmotors.modelo.Cliente;
import com.carmotors.servicios.clientes.ClienteService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de Clientes. Solo delega al Service: sin SQL ni reglas de negocio.
 */
public class ClienteControlador {

    private final ClienteService clienteService;

    public ClienteControlador() {
        this(new ClienteService());
    }

    public ClienteControlador(ClienteService clienteService) {
        if (clienteService == null) {
            throw new IllegalArgumentException("El servicio es obligatorio");
        }
        this.clienteService = clienteService;
    }

    public Cliente registrar(String nombre, String documento, String telefono, String email)
            throws SQLException {
        return clienteService.registrar(nombre, documento, telefono, email);
    }

    public Optional<Cliente> buscarPorId(int id) throws SQLException {
        return clienteService.buscarPorId(id);
    }

    public Optional<Cliente> buscarPorDocumento(String documento) throws SQLException {
        return clienteService.buscarPorDocumento(documento);
    }

    public List<Cliente> listar() throws SQLException {
        return clienteService.listar();
    }

    public boolean actualizarContacto(int id, String telefono, String email) throws SQLException {
        return clienteService.actualizarContacto(id, telefono, email);
    }
}