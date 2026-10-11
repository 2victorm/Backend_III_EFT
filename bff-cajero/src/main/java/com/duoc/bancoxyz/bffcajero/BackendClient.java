package com.duoc.bancoxyz.bffcajero;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
@Service public class BackendClient  {
    private final RestTemplate http;
    public BackendClient(RestTemplate h)  {
        http=h;
    }
    @CircuitBreaker(name="backend",fallbackMethod="sinServicio") public ResponseEntity<Object> llamar(String url,HttpMethod method,Object body,String token,String key)  {
        var h=new HttpHeaders();
        h.set("Authorization",token);
        if(org.slf4j.MDC.get("correlationId")!=null)h.set("X-Correlation-ID",org.slf4j.MDC.get("correlationId"));
        if(key!=null)h.set("Idempotency-Key",key);
        return http.exchange(url,method,new HttpEntity<>(body,h),Object.class);
    }
    public ResponseEntity<Object> sinServicio(String url,HttpMethod method,Object body,String token,String key,Throwable e)  {
        if(e instanceof HttpStatusCodeException h)return ResponseEntity.status(h.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(h.getResponseBodyAsString());
        return ResponseEntity.status(503).body(Map.of("error","Servicio temporalmente no disponible"));
    }
}
@Configuration class HttpConfig  {
    @Bean @LoadBalanced RestTemplate servicios()  {
        var f=new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return new RestTemplate(f);
    }
}
