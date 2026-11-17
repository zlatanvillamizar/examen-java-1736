package com.carmotors.modelo;

public class Vehiculo {

    private int id;
    private int clienteId;
    private String placa;
    private String marca;
    private String modelo;
    private int anio;

    public Vehiculo() {
    }

    public Vehiculo(int clienteId, String placa, String marca,
                    String modelo, int anio) {
        this.clienteId = clienteId;
        this.placa = placa;
        this.marca = marca;
        this.modelo = modelo;
        this.anio = anio;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClienteId() {
        return clienteId;
    }

    public void setClienteId(int clienteId) {
        this.clienteId = clienteId;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    @Override
    public String toString() {
        return id + " | " + placa + " | " + marca
                + " | " + modelo + " | " + anio;
    }
}