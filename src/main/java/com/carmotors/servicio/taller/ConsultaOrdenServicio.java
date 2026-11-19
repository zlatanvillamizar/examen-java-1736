package com.carmotors.servicio.taller;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Consultas de órdenes independientes del mecanismo de persistencia. */
public final class ConsultaOrdenServicio<Id, Orden> {

    private final RepositorioConsultaOrdenServicio<Id, Orden> repositorio;

    public ConsultaOrdenServicio(RepositorioConsultaOrdenServicio<Id, Orden> repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    public Optional<Orden> buscarPorId(Id id) {
        return repositorio.buscarPorId(id);
    }

    /** Devuelve una copia no modificable del listado. */
    public List<Orden> listar() {
        return List.copyOf(repositorio.listar());
    }
}
