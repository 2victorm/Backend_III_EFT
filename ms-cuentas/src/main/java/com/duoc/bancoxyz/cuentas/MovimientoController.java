package com.duoc.bancoxyz.cuentas;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController public class MovimientoController  {
    private final MovimientoService service;
    public MovimientoController(MovimientoService s)  {
        service=s;
    }
    @PostMapping("/internal/movimientos") public Map<String,Object> aplicar(@Valid @RequestBody MovimientoService.Movimiento m)  {
        return service.aplicar(m);
    }
}
