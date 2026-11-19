package com.carmotors.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Factura {
    private int id;
    private int servicioId;
    private LocalDateTime fechaEmision;
    private BigDecimal totalRepuestos = BigDecimal.ZERO;
    private BigDecimal totalManoObra = BigDecimal.ZERO;
    private BigDecimal impuestos = BigDecimal.ZERO;
    private BigDecimal totalFinal = BigDecimal.ZERO;

    private boolean detalleHistorico = true;
    private List<DetalleFactura> detalles = new ArrayList<>();
    private String clienteNombre;
    private String clienteDocumento;
    private String vehiculoDescripcion;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getServicioId() { return servicioId; }
    public void setServicioId(int servicioId) { this.servicioId = servicioId; }
    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }
    public BigDecimal getTotalRepuestos() { return totalRepuestos; }
    public void setTotalRepuestos(BigDecimal totalRepuestos) { this.totalRepuestos = totalRepuestos; }
    public BigDecimal getTotalManoObra() { return totalManoObra; }
    public void setTotalManoObra(BigDecimal totalManoObra) { this.totalManoObra = totalManoObra; }
    public BigDecimal getImpuestos() { return impuestos; }
    public void setImpuestos(BigDecimal impuestos) { this.impuestos = impuestos; }
    public BigDecimal getTotalFinal() { return totalFinal; }
    public void setTotalFinal(BigDecimal totalFinal) { this.totalFinal = totalFinal; }
    public boolean isDetalleHistorico() { return detalleHistorico; }
    public void setDetalleHistorico(boolean detalleHistorico) { this.detalleHistorico = detalleHistorico; }
    public List<DetalleFactura> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleFactura> detalles) {
        this.detalles = detalles == null ? new ArrayList<>() : new ArrayList<>(detalles);
    }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteDocumento() { return clienteDocumento; }
    public void setClienteDocumento(String clienteDocumento) { this.clienteDocumento = clienteDocumento; }
    public String getVehiculoDescripcion() { return vehiculoDescripcion; }
    public void setVehiculoDescripcion(String v) { this.vehiculoDescripcion = v; }

    @Override
    public String toString() {
        return id + " | servicio=" + servicioId + " | " + fechaEmision
                + " | repuestos=" + totalRepuestos + " | mano de obra=" + totalManoObra
                + " | impuestos=" + impuestos + " | total=" + totalFinal;
    }
}