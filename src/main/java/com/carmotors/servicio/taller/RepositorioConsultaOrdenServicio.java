package com.carmotors.servicio.taller;

import java.util.List;
import java.util.Optional;

/** Puerto de lectura para un adaptador de persistencia de órdenes. */
public interface RepositorioConsultaOrdenServicio<Id, Orden> {

    /** Devuelve un Optional vacío cuando la orden no existe. */
    Optional<Orden> buscarPorId(Id id);

    /** Devuelve una lista vacía cuando no existen órdenes. */
    List<Orden> listar();
}
