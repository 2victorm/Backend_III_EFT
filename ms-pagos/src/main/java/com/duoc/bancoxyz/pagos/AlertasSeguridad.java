package com.duoc.bancoxyz.pagos;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;
@Component public class AlertasSeguridad  {
    private final KafkaTemplate<String,String> kafka;
    public AlertasSeguridad(KafkaTemplate<String,String> k)  {
        kafka=k;
    }
    public void publicar(String tipo)  {
        try  {
            kafka.send("alertas-seguridad",UUID.randomUUID().toString(),tipo);
        }
        catch(Exception ignored)  {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo enviar alerta de seguridad");
        }
    }
}
