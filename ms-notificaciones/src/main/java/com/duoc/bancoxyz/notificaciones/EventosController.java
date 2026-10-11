package com.duoc.bancoxyz.notificaciones;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController public class EventosController  {
    private final JdbcTemplate db;
    public EventosController(JdbcTemplate db)  {
        this.db=db;
    }
    @KafkaListener(topics=  {
        "transacciones-completadas","alertas-seguridad"
    }
    // Guarda el evento y evita duplicados mediante su identificador único.
    ,groupId="auditoria-banco") public void recibir(ConsumerRecord<String,String> r)  {
        int n=db.update("INSERT INTO eventos(evento_id,topico,contenido) VALUES(?,?,?) ON CONFLICT(evento_id) DO NOTHING",r.key(),r.topic(),r.value());
        if(n==1)org.slf4j.LoggerFactory.getLogger(getClass()).info("{}: {}",r.topic(),r.value());
    }
    // Permite consultar los últimos eventos registrados para revisar las operaciones.
    @GetMapping("/api/eventos") public List<Map<String,Object>> listar()  {
        return db.queryForList("SELECT * FROM eventos ORDER BY recibido DESC LIMIT 100");
    }
}
