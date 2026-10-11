package com.duoc.bancoxyz.clientes;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/api/clientes") public class ClienteController  {
    private final JdbcTemplate db;
    public ClienteController(JdbcTemplate db)  {
        this.db=db;
    }
    public record Cliente(@NotBlank String rut,@NotBlank String nombre,@NotBlank @Email String email)  {
    }
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name="clientesDb",fallbackMethod="noDisponible") @GetMapping public List<Map<String,Object>> listar()  {
        return db.queryForList("SELECT * FROM clientes ORDER BY id");
    }
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name="clientesDb",fallbackMethod="noDisponible") @GetMapping("/{id}") public ResponseEntity<?> obtener(@PathVariable long id)  {
        var r=db.queryForList("SELECT * FROM clientes WHERE id=?",id);
        return r.isEmpty()?ResponseEntity.notFound().build():ResponseEntity.ok(r.get(0));
    }
    @PostMapping public ResponseEntity<?> crear(@Valid @RequestBody Cliente c)  {
        Long id=db.queryForObject("INSERT INTO clientes(rut,nombre,email) VALUES(?,?,?) RETURNING id",Long.class,c.rut(),c.nombre(),c.email());
        return ResponseEntity.status(201).body(Map.of("id",id));
    }
    @PutMapping("/{id}") public ResponseEntity<?> actualizar(@PathVariable long id,@Valid @RequestBody Cliente c)  {
        int n=db.update("UPDATE clientes SET rut=?,nombre=?,email=? WHERE id=?",c.rut(),c.nombre(),c.email(),id);
        return n==0?ResponseEntity.notFound().build():obtener(id);
    }
    public List<Map<String,Object>> noDisponible(Throwable error) {
        throw new ServicioNoDisponible("Servicio de clientes temporalmente no disponible");
    }
    public ResponseEntity<?> noDisponible(long id,Throwable error) {
        throw new ServicioNoDisponible("Servicio de clientes temporalmente no disponible");
    }
}
