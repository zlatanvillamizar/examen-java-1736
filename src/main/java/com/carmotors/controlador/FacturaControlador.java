package com.carmotors.controlador;

import com.carmotors.modelo.Factura;
import com.carmotors.servicios.facturacion.FacturaService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class FacturaControlador {

    private final FacturaService service;

    public FacturaControlador() {
        this(new FacturaService());
    }

    public FacturaControlador(FacturaService service) {
        if (service == null) {
            throw new IllegalArgumentException("El servicio es obligatorio");
        }
        this.service = service;
    }

    public Factura crear(int servicioId) throws SQLException {
        return service.crear(servicioId);
    }

    public Optional<Factura> buscarPorId(int id) throws SQLException {
        return service.buscarPorId(id);
    }

    public List<Factura> listar() throws SQLException {
        return service.listar();
    }
}