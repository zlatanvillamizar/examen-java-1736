package com.carmotors.servicio.taller.integracion;

/**
 * Contrato que Servicios usa para solicitar movimientos de inventario.
 * La implementación pertenece al módulo de Inventario: Servicios no modifica stock ni lotes directamente.
 */
public interface InventarioServiciosPort {

    void reservar(ReservaRepuesto solicitud);

    void consumir(ConsumoRepuesto solicitud);

    void liberar(ReservaRepuesto solicitud);
}
