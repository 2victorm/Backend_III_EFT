package com.duoc.bancoxyz.pagos;

import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final JdbcTemplate db;

    public PagoController(JdbcTemplate db) {
        this.db = db;
    }

    public record Pago(@Positive long cuentaId, Long destinoId, @NotNull
            @DecimalMin("0.01")
            @Digits(integer = 13, fraction = 2) BigDecimal monto, @NotNull
            @Pattern(regexp = "DEPOSITO|PAGO|TRANSFERENCIA|RETIRO") String tipo) {

    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestHeader("Idempotency-Key") UUID id, @Valid @RequestBody Pago p) {
        if (p.tipo().equals("TRANSFERENCIA") && (p.destinoId() == null || p.destinoId() <= 0 || p.destinoId() == p.cuentaId())) {
            throw new IllegalArgumentException("Destino invalido");
        }
        if (!p.tipo().equals("TRANSFERENCIA") && p.destinoId() != null) {
            throw new IllegalArgumentException("Solo las transferencias requieren destino");
        }
        int n = db.update("INSERT INTO pagos(id,cuenta_id,destino_id,monto,tipo,estado) VALUES(?,?,?,?,?,'PENDIENTE') ON CONFLICT(id) DO NOTHING", id, p.cuentaId(), p.destinoId(), p.monto(), p.tipo());
        var r = db.queryForMap("SELECT * FROM pagos WHERE id=?", id);
        if (((Number) r.get("cuenta_id")).longValue() != p.cuentaId() || ((BigDecimal) r.get("monto")).compareTo(p.monto()) != 0 || !r.get("tipo").equals(p.tipo()) || !Objects.equals(r.get("destino_id"), p.destinoId())) {
            return ResponseEntity.status(409).body(Map.of("error", "La clave ya corresponde a otra solicitud"));
        }
        return ResponseEntity.status(n == 1 ? 202 : 200).body(r);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return db.queryForList("SELECT * FROM pagos ORDER BY creado DESC LIMIT 100");
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable UUID id) {
        var r = db.queryForList("SELECT * FROM pagos WHERE id=?", id);
        return r.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(r.get(0));
    }
}
