package com.duoc.bancoxyz.bffcajero;
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
    @PostMapping("/retiros") public ResponseEntity<?> retirar(@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t,@RequestHeader("Idempotency-Key") String key)  {
        var retiro=new HashMap<String,Object>();
        retiro.put("cuentaId",body.get("cuentaId"));
        retiro.put("monto",body.get("monto"));
        retiro.put("tipo","RETIRO");
        return backend.llamar("http://ms-pagos/api/pagos",HttpMethod.POST,retiro,t,key);
    }
}
