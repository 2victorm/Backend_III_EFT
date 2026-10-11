package com.duoc.bancoxyz.pagos;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
@Component public class PagoScheduler  {
    private final JdbcTemplate db;
    private final CuentasClient cuentas;
    private final KafkaTemplate<String,String> kafka;
    private static final Logger log=LoggerFactory.getLogger(PagoScheduler.class);
    public PagoScheduler(JdbcTemplate db,CuentasClient cuentas,KafkaTemplate<String,String> kafka)  {
        this.db=db;
        this.cuentas=cuentas;
        this.kafka=kafka;
    }
    // Ejecuta periódicamente el procesamiento de operaciones pendientes.
    @Scheduled(fixedDelayString="${banco.pagos-delay:5000}") public void pendientes()  {
        for(var p:db.queryForList("SELECT * FROM pagos WHERE estado='PENDIENTE' ORDER BY creado LIMIT 20"))  {
            try  {
                cuentas.aplicar(p);
                db.update("UPDATE pagos SET estado='APLICADA',motivo=NULL WHERE id=? AND estado='PENDIENTE'",p.get("id"));
            }
            catch(HttpClientErrorException.BadRequest e)  {
                db.update("UPDATE pagos SET estado='RECHAZADA',motivo=? WHERE id=? AND estado='PENDIENTE'","Solicitud invalida o saldo insuficiente",p.get("id"));
            }
            catch(Exception e)  {
                log.warn("Pago {} sigue pendiente: {}",p.get("id"),e.getClass().getSimpleName());
            }
        }
        // La fila es el registro persistente del evento pendiente: se marca solo tras confirmacion Kafka.
        for(var p:db.queryForList("SELECT * FROM pagos WHERE estado IN ('APLICADA','RECHAZADA') AND NOT publicado ORDER BY creado LIMIT 20"))  {
            try  {
                String value=p.get("id")+"|"+p.get("estado")+"|"+p.get("tipo")+"|"+p.get("cuenta_id")+"|"+p.get("monto");
                kafka.send("transacciones-completadas",p.get("id").toString(),value).get(5,TimeUnit.SECONDS);
                db.update("UPDATE pagos SET publicado=true WHERE id=?",p.get("id"));
                log.info("Evento confirmado {}",value);
            }
            catch(Exception e)  {
                log.warn("Evento {} pendiente de publicacion",p.get("id"));
            }
        }
    }
}
