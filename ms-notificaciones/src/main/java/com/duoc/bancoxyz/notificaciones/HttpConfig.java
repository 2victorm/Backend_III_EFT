package com.duoc.bancoxyz.notificaciones;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
@Configuration public class HttpConfig  {
    @Bean @LoadBalanced RestTemplate servicios()  {
        var f=new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(5000);
        return new RestTemplate(f);
    }
}
