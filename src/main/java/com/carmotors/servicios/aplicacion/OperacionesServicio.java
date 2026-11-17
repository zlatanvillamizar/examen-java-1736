package com.carmotors.servicios.aplicacion;

import com.carmotors.servicios.dominio.IdentificadorServicio;
import com.carmotors.servicios.dominio.IdentificadorVehiculo;

import java.util.List;
import java.util.Optional;

/**
 * Contrato del caso de uso de servicios, pendiente de conectar a JDBC.
 *
 * Los IDs de técnicos, repuestos y estados se expresan como referencias lógicas
 * hasta que el equipo apruebe el DER y sus tipos físicos.
 * Ninguna implementación debe inferir columnas SQL de este contrato.
 */
public interface OperacionesServicio {
    IdentificadorServicio abrirOrden(IdentificadorVehiculo vehiculo);

    Optional<IdentificadorServicio> buscarPorId(IdentificadorServicio servicio);

    List<IdentificadorServicio> listarOrdenes();

    void cambiarEstado(IdentificadorServicio servicio, long identificadorEstado);

    void asignarTecnico(IdentificadorServicio servicio, long identificadorTecnico);

    void asociarRepuesto(IdentificadorServicio servicio, long identificadorRepuesto);
}
