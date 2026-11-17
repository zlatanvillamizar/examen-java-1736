package com.carmotors.dao;

/** Se lanza cuando se opera sobre un repuesto que no existe. */
public class RepuestoNoEncontradoException extends RuntimeException {

    private final int repuestoId;

    public RepuestoNoEncontradoException(int repuestoId) {
        super("No existe el repuesto con id " + repuestoId);
        this.repuestoId = repuestoId;
    }

    public int getRepuestoId() {
        return repuestoId;
    }
}
