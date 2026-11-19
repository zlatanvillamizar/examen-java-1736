package com.carmotors.servicio.taller;

/**
 * Casos de uso de escritura de órdenes de servicio.
 * Los tipos de entrada representan solicitudes de aplicación y podrán definirse
 * al confirmar los datos requeridos para cada operación.
 */
public interface OperacionesOrdenServicio<Id, Solicitud, CambioEstado, AsignacionTecnico, AsignacionRepuesto> {

    Id crear(Solicitud solicitud);

    void cambiarEstado(Id id, CambioEstado cambio);

    void asignarTecnico(Id id, AsignacionTecnico asignacion);

    void asignarRepuesto(Id id, AsignacionRepuesto asignacion);
}
