package com.carmotors.modelo;

/** El ID es nulo en una solicitud de alta y se genera al guardar. */
public record Tecnico(Integer id, String nombre, String especialidad, boolean activo) {
}
