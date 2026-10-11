package com.duoc.bancoxyz.bffweb;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class BffController  {
    private final BackendClient backend;
    public BffController(BackendClient b)  {
        backend=b;
    }
    @GetMapping("/cuentas/{id}/saldo") public ResponseEntity<?> saldo(@PathVariable long id,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-cuentas/api/cuentas/"+id+"/saldo",HttpMethod.GET,null,t,null);
    }
    @GetMapping("/pagos/{id}") public ResponseEntity<?> pago(@PathVariable UUID id,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-pagos/api/pagos/"+id,HttpMethod.GET,null,t,null);
    }
    @GetMapping("/clientes") public ResponseEntity<?> clientes(@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-clientes/api/clientes",HttpMethod.GET,null,t,null);
    }
    @PostMapping("/clientes") public ResponseEntity<?> cliente(@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-clientes/api/clientes",HttpMethod.POST,body,t,null);
    }
    @PutMapping("/clientes/{id}") public ResponseEntity<?> clienteEditar(@PathVariable long id,@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-clientes/api/clientes/"+id,HttpMethod.PUT,body,t,null);
    }
    @GetMapping("/cuentas") public ResponseEntity<?> cuentas(@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-cuentas/api/cuentas",HttpMethod.GET,null,t,null);
    }
    @PostMapping("/cuentas") public ResponseEntity<?> abrir(@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-cuentas/api/cuentas",HttpMethod.POST,body,t,null);
    }
    @PutMapping("/cuentas/{id}/cerrar") public ResponseEntity<?> cerrar(@PathVariable long id,@RequestHeader("Authorization") String t)  {
        return backend.llamar("http://ms-cuentas/api/cuentas/"+id+"/cerrar",HttpMethod.PUT,null,t,null);
    }
    @GetMapping("/resumen/{clienteId}") public ResponseEntity<?> resumen(@PathVariable long clienteId,@RequestHeader("Authorization") String t)  {
        var c=backend.llamar("http://ms-clientes/api/clientes/"+clienteId,HttpMethod.GET,null,t,null);
        if(!c.getStatusCode().is2xxSuccessful())return c;
        var cuentas=backend.llamar("http://ms-cuentas/api/cuentas?clienteId="+clienteId,HttpMethod.GET,null,t,null);
        if(!cuentas.getStatusCode().is2xxSuccessful())return cuentas;
        return ResponseEntity.ok(Map.of("cliente",c.getBody(),"cuentas",cuentas.getBody()));
    }
    @GetMapping("/batch/{reporte}") public ResponseEntity<?> batch(@PathVariable String reporte,@RequestParam(defaultValue="semana_1") String conjunto,@RequestHeader("Authorization") String t)  {
        if(!Set.of("transacciones","intereses","anuales","rechazos").contains(reporte)||!Set.of("semana_1","semana_2","semana_3").contains(conjunto))return ResponseEntity.badRequest().build();
        return backend.llamar("http://banco-batch/api/batch/"+reporte+"?conjunto="+conjunto,HttpMethod.GET,null,t,null);
    }
    @PostMapping("/pagos") public ResponseEntity<?> crearPago(@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t,@RequestHeader("Idempotency-Key") String key)  {
        return backend.llamar("http://ms-pagos/api/pagos",HttpMethod.POST,body,t,key);
    }
    @PutMapping("/cuentas/{id}") public ResponseEntity<?> mantener(@PathVariable long id,@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t) {
        return backend.llamar("http://ms-cuentas/api/cuentas/"+id,HttpMethod.PUT,body,t,null);
    }
}
