package com.duoc.bancoxyz.pagos;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
@Configuration public class KafkaConfig  {
    @Bean NewTopic transacciones()  {
        return TopicBuilder.name("transacciones-completadas").partitions(3).replicas(1).build();
    }
    @Bean NewTopic seguridad()  {
        return TopicBuilder.name("alertas-seguridad").partitions(3).replicas(1).build();
    }
}
