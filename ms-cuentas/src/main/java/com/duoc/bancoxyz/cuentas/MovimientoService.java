package com.duoc.bancoxyz.cuentas;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
@Service public class MovimientoService  {
    private final JdbcTemplate db;
    public MovimientoService(JdbcTemplate db)  {
        this.db=db;
    }
    public record Movimiento(@NotNull UUID id,@Positive long cuentaId,Long destinoId,@NotNull @DecimalMin("0.01") @Digits(integer=13,fraction=2) BigDecimal monto,@NotNull @Pattern(regexp="DEPOSITO|PAGO|TRANSFERENCIA|RETIRO") String tipo)  {
    }
    @Transactional public Map<String,Object> aplicar(Movimiento m)  {
        // PostgreSQL serializa la misma clave entre instancias. Si falla, toda la operacion revierte.
        int nuevo=db.update("INSERT INTO operaciones_cuenta(id,cuenta_id,destino_id,monto,tipo) VALUES(?,?,?,?,?) ON CONFLICT(id) DO NOTHING",m.id(),m.cuentaId(),m.destinoId(),m.monto(),m.tipo());
        if(nuevo==0)  {
            var previo=db.queryForMap("SELECT * FROM operaciones_cuenta WHERE id=?",m.id());
            if(((Number)previo.get("cuenta_id")).longValue()!=m.cuentaId() || ((BigDecimal)previo.get("monto")).compareTo(m.monto())!=0 || !previo.get("tipo").equals(m.tipo()) || !Objects.equals(previo.get("destino_id"),m.destinoId()))throw new IllegalArgumentException("La clave corresponde a otra operacion");
            return Map.of("estado","APLICADA","repetida",true);
        }
        if(m.tipo().equals("TRANSFERENCIA") && (m.destinoId()==null || m.destinoId()==m.cuentaId()))throw new IllegalArgumentException("Cuenta destino invalida");
        var ids=m.tipo().equals("TRANSFERENCIA")?List.of(m.cuentaId(),m.destinoId()).stream().sorted().toList():List.of(m.cuentaId());
        for(long id:ids)  {
            var r=db.queryForList("SELECT id FROM cuentas WHERE id=? AND estado='ACTIVA' FOR UPDATE",id);
            if(r.isEmpty())throw new IllegalArgumentException("Cuenta inexistente o cerrada: "+id);
        }
        if(m.tipo().equals("DEPOSITO"))db.update("UPDATE cuentas SET saldo=saldo+? WHERE id=?",m.monto(),m.cuentaId());
        else  {
            int n=db.update("UPDATE cuentas SET saldo=saldo-? WHERE id=? AND saldo>=?",m.monto(),m.cuentaId(),m.monto());
            if(n==0)throw new IllegalArgumentException("Saldo insuficiente");
            if(m.tipo().equals("TRANSFERENCIA"))db.update("UPDATE cuentas SET saldo=saldo+? WHERE id=?",m.monto(),m.destinoId());
        }
        return Map.of("estado","APLICADA","repetida",false);
    }
}
