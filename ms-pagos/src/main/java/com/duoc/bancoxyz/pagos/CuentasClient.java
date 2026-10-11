package com.duoc.bancoxyz.pagos;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
@Service public class CuentasClient  {
    private final RestTemplate servicios;
    private final RestTemplate auth;
    private final String authUrl;
    private final String secret;
    public CuentasClient(RestTemplate servicios,@Value("${banco.auth-url}") String url,@Value("${banco.servicios-secret}") String secret)  {
        this.servicios=servicios;
        this.authUrl=url;
        this.secret=secret;
        var f=new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        auth=new RestTemplate(f);
    }
    @CircuitBreaker(name="cuentas",fallbackMethod="pendiente") public Map<?,?> aplicar(Map<String,Object> pago)  {
        var h=new HttpHeaders();
        h.setBasicAuth("servicios",secret);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var form=new LinkedMultiValueMap<String,String>();
        form.add("grant_type","client_credentials");
        form.add("scope","banco.internal");
        var token=auth.postForObject(authUrl+"/oauth2/token",new HttpEntity<>(form,h),Map.class);
        var cabecera=new HttpHeaders();
        cabecera.setBearerAuth((String)token.get("access_token"));
        var body=new java.util.HashMap<String,Object>();
        body.put("id",pago.get("id"));
        body.put("cuentaId",pago.get("cuenta_id"));
        body.put("destinoId",pago.get("destino_id"));
        body.put("monto",pago.get("monto"));
        body.put("tipo",pago.get("tipo"));
        return servicios.postForObject("http://ms-cuentas/internal/movimientos",new HttpEntity<>(body,cabecera),Map.class);
    }
    public Map<?,?> pendiente(Map<String,Object> pago,Throwable error)  {
        // Un error de negocio es definitivo; la falta de conectividad se vuelve a intentar.
        if(error instanceof org.springframework.web.client.HttpClientErrorException.BadRequest e)throw e;
        throw new ServicioNoDisponible("Servicio de cuentas temporalmente no disponible");
    }
}
