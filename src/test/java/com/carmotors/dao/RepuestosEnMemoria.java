package com.carmotors.dao;

import com.carmotors.modelo.Repuesto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link RepuestoDAO} en memoria para probar la lógica de inventario sin base de datos. */
public final class RepuestosEnMemoria extends RepuestoDAO {

    private final Map<Integer, Repuesto> datos = new LinkedHashMap<>();
    private final Map<Integer, String> tipos = new LinkedHashMap<>(Map.of(1, "Filtros"));
    private int movimientos;

    public RepuestosEnMemoria() {
        super(SinBaseDeDatos.FUENTE);
    }

    /** Stock actual del repuesto guardado con ese id. */
    public int stock(int id) {
        return datos.get(id).cantidadStock();
    }

    /** Cantidad de entradas o salidas que llegaron al DAO. */
    public int movimientos() {
        return movimientos;
    }

    public int cantidadGuardados() {
        return datos.size();
    }

    /** Reemplaza el catálogo de tipos (por defecto: 1 = Filtros). */
    public void tipos(Map<Integer, String> nuevos) {
        tipos.clear();
        tipos.putAll(nuevos);
    }

    @Override
    public Map<Integer, String> listarTipos() {
        return new LinkedHashMap<>(tipos);
    }

    @Override
    public List<Repuesto> listar() {
        return new ArrayList<>(datos.values());
    }

    @Override
    public Optional<Repuesto> buscarPorId(int id) {
        return Optional.ofNullable(datos.get(id));
    }

    @Override
    public Repuesto registrar(Repuesto repuesto) {
        Repuesto guardado = repuesto.conId(datos.size() + 1);
        datos.put(guardado.id(), guardado);
        return guardado;
    }

    @Override
    public int registrarEntrada(int repuestoId, int cantidad) {
        movimientos++;
        return cambiarStock(repuestoId, stock(repuestoId) + cantidad);
    }

    @Override
    public int registrarSalida(int repuestoId, int cantidad) {
        movimientos++;
        int disponible = stock(repuestoId);
        if (cantidad > disponible) {
            throw new StockInsuficienteException(repuestoId, cantidad, disponible);
        }
        return cambiarStock(repuestoId, disponible - cantidad);
    }

    private int cambiarStock(int id, int nuevoStock) {
        Repuesto r = datos.get(id);
        datos.put(id, new Repuesto(id, r.nombre(), r.marca(), r.repuestoTipoId(),
                r.precioVenta(), nuevoStock, r.stockMinimo()));
        return nuevoStock;
    }
}
