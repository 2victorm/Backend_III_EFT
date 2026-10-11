package com.duoc.bancoxyz.cuentas;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
@RestController @RequestMapping("/api/cuentas") public class CuentaController  {
    private final JdbcTemplate db;
    private final RestTemplate http;
    public CuentaController(JdbcTemplate db,RestTemplate http)  {
        this.db=db;
        this.http=http;
    }
    public record Apertura(@Positive long clienteId,@Pattern(regexp="AHORRO|PRESTAMO") @NotNull String tipo,@NotNull @DecimalMin("0") @Digits(integer=13,fraction=2) BigDecimal saldo)  {
    }
    @GetMapping public List<Map<String,Object>> listar(@RequestParam(required=false) Long clienteId)  {
        return clienteId==null?db.queryForList("SELECT * FROM cuentas ORDER BY id"):db.queryForList("SELECT * FROM cuentas WHERE cliente_id=? ORDER BY id",clienteId);
    }
    @GetMapping("/{id}") public ResponseEntity<?> obtener(@PathVariable long id)  {
        var r=db.queryForList("SELECT * FROM cuentas WHERE id=?",id);
        return r.isEmpty()?ResponseEntity.notFound().build():ResponseEntity.ok(r.get(0));
    }
    @GetMapping("/{id}/saldo") public ResponseEntity<?> saldo(@PathVariable long id)  {
        var r=db.queryForList("SELECT id,saldo,estado FROM cuentas WHERE id=?",id);
        return r.isEmpty()?ResponseEntity.notFound().build():ResponseEntity.ok(r.get(0));
    }
    @PostMapping @CircuitBreaker(name="clientes",fallbackMethod="sinClientes") public ResponseEntity<?> abrir(@Valid @RequestBody Apertura c,@RequestHeader("Authorization") String token)  {
        http.exchange("http://ms-clientes/api/clientes/"+c.clienteId(),HttpMethod.GET,new HttpEntity<>(headers(token)),Map.class);
        Long id=db.queryForObject("INSERT INTO cuentas(cliente_id,tipo,saldo,estado) VALUES(?,?,?,'ACTIVA') RETURNING id",Long.class,c.clienteId(),c.tipo(),c.saldo());
        return ResponseEntity.status(201).body(Map.of("id",id));
    }
    public ResponseEntity<?> sinClientes(Apertura c,String token,Throwable e)  {
        if(e instanceof org.springframework.web.client.HttpClientErrorException.NotFound)return ResponseEntity.badRequest().body(Map.of("error","Cliente inexistente"));
        throw new ServicioNoDisponible("No se pudo verificar el cliente; vuelve a intentar");
    }
    @PutMapping("/{id}/cerrar") public ResponseEntity<?> cerrar(@PathVariable long id)  {
        int n=db.update("UPDATE cuentas SET estado='CERRADA' WHERE id=? AND saldo=0 AND estado='ACTIVA'",id);
        return n==0?ResponseEntity.status(409).body(Map.of("error","La cuenta debe estar activa y con saldo cero")):obtener(id);
    }
    private HttpHeaders headers(String token)  {
        var h=new HttpHeaders();
        h.set("Authorization",token);
        if(org.slf4j.MDC.get("correlationId")!=null)h.set("X-Correlation-ID",org.slf4j.MDC.get("correlationId"));
        return h;
    }
    public record Mantenimiento(@NotNull @Pattern(regexp="AHORRO|PRESTAMO") String tipo) {
    }
    @PutMapping("/{id}") public ResponseEntity<?> mantener(@PathVariable long id,@Valid @RequestBody Mantenimiento m) {
        int n=db.update("UPDATE cuentas SET tipo=? WHERE id=? AND estado='ACTIVA'",m.tipo(),id);
        return n==0?ResponseEntity.status(409).body(Map.of("error","Cuenta inexistente o cerrada")):obtener(id);
    }
}
