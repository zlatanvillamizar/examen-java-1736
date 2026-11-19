package com.carmotors.servicios.facturacion;

import com.carmotors.dao.FacturaDAO;
import com.carmotors.modelo.DetalleFactura;
import com.carmotors.modelo.Factura;
import com.carmotors.modelo.ServicioFacturable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class FacturaService {

    /** Pendiente de aprobación del líder. */
    private static final int ESCALA = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final String ESTADO_FACTURABLE = "Completado";

    private final FacturaDAO facturaDAO;
    private final BigDecimal tasaImpuesto; // fracción: 0.00 a 1.00

    /** Tasa 0% hasta que el líder apruebe la tasa real. No se inventa ninguna. */
    public FacturaService() {
        this(new FacturaDAO(), BigDecimal.ZERO);
    }

    public FacturaService(FacturaDAO facturaDAO, BigDecimal tasaImpuesto) {
        if (facturaDAO == null) {
            throw new IllegalArgumentException("El DAO es obligatorio");
        }
        if (tasaImpuesto == null || tasaImpuesto.signum() < 0
                || tasaImpuesto.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La tasa de impuesto debe estar entre 0 y 1");
        }
        this.facturaDAO = facturaDAO;
        this.tasaImpuesto = tasaImpuesto;
    }

    public Factura crear(int servicioId) throws SQLException {
        validarId(servicioId, "El servicio");
        ServicioFacturable servicio = facturaDAO.buscarServicioFacturable(servicioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el servicio " + servicioId + " (o su vehículo/cliente)"));
        if (!ESTADO_FACTURABLE.equalsIgnoreCase(servicio.estado())) {
            throw new IllegalStateException("Solo se factura un servicio en estado "
                    + ESTADO_FACTURABLE + " (actual: " + servicio.estado() + ")");
        }
        if (facturaDAO.buscarPorServicio(servicioId).isPresent()) {
            throw new IllegalStateException("El servicio " + servicioId + " ya tiene factura");
        }
        Factura factura = calcular(servicio);
        facturaDAO.crear(factura);
        return factura;
    }

    /** Cálculo puro, sin acceso a la base de datos. */
    public Factura calcular(ServicioFacturable servicio) {
        if (servicio == null) {
            throw new IllegalArgumentException("El servicio es obligatorio");
        }
        BigDecimal manoObra = servicio.costoManoObra() == null
                ? BigDecimal.ZERO : servicio.costoManoObra();
        if (manoObra.signum() < 0) {
            throw new IllegalArgumentException("La mano de obra no puede ser negativa");
        }
        BigDecimal repuestos = servicio.detalles().stream()
                .map(DetalleFactura::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA, REDONDEO);
        manoObra = manoObra.setScale(ESCALA, REDONDEO);
        BigDecimal base = repuestos.add(manoObra);
        BigDecimal impuestos = base.multiply(tasaImpuesto).setScale(ESCALA, REDONDEO);

        Factura f = new Factura();
        f.setServicioId(servicio.servicioId());
        f.setFechaEmision(LocalDateTime.now());
        f.setTotalRepuestos(repuestos);
        f.setTotalManoObra(manoObra);
        f.setImpuestos(impuestos);
        f.setTotalFinal(base.add(impuestos));
        enriquecer(f, servicio);
        return f;
    }

    public Optional<Factura> buscarPorId(int id) throws SQLException {
        validarId(id, "El ID");
        return facturaDAO.buscarPorId(id);
    }

    public List<Factura> listar() throws SQLException {
        return facturaDAO.listar();
    }

    private void enriquecer(Factura f, ServicioFacturable s) {
        f.setDetalles(s.detalles());
        f.setClienteNombre(s.clienteNombre());
        f.setClienteDocumento(s.clienteDocumento());
        f.setVehiculoDescripcion(s.vehiculoDescripcion());
    }

    private void validarId(int id, String campo) {
        if (id <= 0) {
            throw new IllegalArgumentException(campo + " debe ser un número positivo");
        }
    }
}