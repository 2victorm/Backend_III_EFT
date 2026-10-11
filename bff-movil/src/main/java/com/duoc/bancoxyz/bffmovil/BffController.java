package com.duoc.bancoxyz.bffmovil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    // Consulta las cuentas del cliente y devuelve un resumen para el canal móvil.
    @GetMapping("/resumen/{clienteId}") public ResponseEntity<?> resumen(@PathVariable long clienteId,@RequestHeader("Authorization") String t)  {
        var r=backend.llamar("http://ms-cuentas/api/cuentas?clienteId="+clienteId,HttpMethod.GET,null,t,null);
        if(!r.getStatusCode().is2xxSuccessful())return r;
        var ligero=new ArrayList<Map<String,Object>>();
        for(Object o:(List<?>)r.getBody())  {
            Map<?,?> c=(Map<?,?>)o;
            ligero.add(Map.of("id",c.get("id"),"saldo",c.get("saldo"),"estado",c.get("estado")));
        }
        return ResponseEntity.ok(Map.of("cuentas",ligero));
    }
    @PostMapping("/pagos") public ResponseEntity<?> crearPago(@RequestBody Map<String,Object> body,@RequestHeader("Authorization") String t,@RequestHeader("Idempotency-Key") String key)  {
        return backend.llamar("http://ms-pagos/api/pagos",HttpMethod.POST,body,t,key);
    }
}
