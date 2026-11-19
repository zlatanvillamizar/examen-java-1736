package com.carmotors.dao;

import com.carmotors.modelo.Proveedor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link ProveedorDAO} en memoria para probar la lógica de inventario sin base de datos. */
public final class ProveedoresEnMemoria extends ProveedorDAO {

    private final Map<Integer, Proveedor> datos = new LinkedHashMap<>();

    public ProveedoresEnMemoria() {
        super(SinBaseDeDatos.FUENTE);
    }

    public int cantidadGuardados() {
        return datos.size();
    }

    @Override
    public List<Proveedor> listar() {
        return new ArrayList<>(datos.values());
    }

    @Override
    public Optional<Proveedor> buscarPorId(int id) {
        return Optional.ofNullable(datos.get(id));
    }

    /** Igual que la columna con collation {@code utf8mb4_unicode_ci}: no distingue mayúsculas. */
    @Override
    public Optional<Proveedor> buscarPorNombre(String nombre) {
        return datos.values().stream()
                .filter(p -> p.nombre().equalsIgnoreCase(nombre.strip()))
                .findFirst();
    }

    @Override
    public Proveedor registrar(Proveedor proveedor) {
        Proveedor guardado = proveedor.conId(datos.size() + 1);
        datos.put(guardado.id(), guardado);
        return guardado;
    }

    @Override
    public boolean actualizarContacto(Proveedor proveedor) {
        if (!datos.containsKey(proveedor.id())) {
            return false;
        }
        datos.put(proveedor.id(), proveedor);
        return true;
    }
}
