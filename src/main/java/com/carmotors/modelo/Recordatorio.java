package com.carmotors.modelo;

import java.time.LocalDate;

public class Recordatorio {
    private int id;
    private int vehiculoId;
    private Integer servicioOrigenId;
    private LocalDate fechaProximoMantenimiento;
    private String descripcion;
    private boolean notificado;

    public Recordatorio() {}

    public Recordatorio(int vehiculoId, Integer servicioOrigenId,
                        LocalDate fechaProximoMantenimiento, String descripcion) {
        this.vehiculoId = vehiculoId;
        this.servicioOrigenId = servicioOrigenId;
        this.fechaProximoMantenimiento = fechaProximoMantenimiento;
        this.descripcion = descripcion;
        this.notificado = false;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getVehiculoId() { return vehiculoId; }
    public void setVehiculoId(int vehiculoId) { this.vehiculoId = vehiculoId; }
    public Integer getServicioOrigenId() { return servicioOrigenId; }
    public void setServicioOrigenId(Integer servicioOrigenId) { this.servicioOrigenId = servicioOrigenId; }
    public LocalDate getFechaProximoMantenimiento() { return fechaProximoMantenimiento; }
    public void setFechaProximoMantenimiento(LocalDate f) { this.fechaProximoMantenimiento = f; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isNotificado() { return notificado; }
    public void setNotificado(boolean notificado) { this.notificado = notificado; }

    @Override
    public String toString() {
        return id + " | vehiculo=" + vehiculoId + " | servicio=" + servicioOrigenId
                + " | " + fechaProximoMantenimiento + " | " + descripcion
                + " | notificado=" + notificado;
    }
}