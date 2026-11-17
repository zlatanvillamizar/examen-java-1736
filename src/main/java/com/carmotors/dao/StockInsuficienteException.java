package com.carmotors.dao;

/** Se lanza cuando una salida dejaría el stock de un repuesto en negativo. */
public class StockInsuficienteException extends RuntimeException {

    private final int repuestoId;
    private final int solicitado;
    private final int disponible;

    public StockInsuficienteException(int repuestoId, int solicitado, int disponible) {
        super("Stock insuficiente para el repuesto " + repuestoId
                + ": se pidieron " + solicitado + " y hay " + disponible);
        this.repuestoId = repuestoId;
        this.solicitado = solicitado;
        this.disponible = disponible;
    }

    public int getRepuestoId() {
        return repuestoId;
    }

    public int getSolicitado() {
        return solicitado;
    }

    public int getDisponible() {
        return disponible;
    }
}
