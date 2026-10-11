package com.duoc.bancoxyz.batch;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/batch") public class ReportesController  {
    private final JdbcTemplate db;
    public ReportesController(JdbcTemplate db)  {
        this.db=db;
    }
    @GetMapping("/transacciones") public List<Map<String,Object>> transacciones(@RequestParam(defaultValue="semana_1") String conjunto)  {
        return db.queryForList("SELECT fecha,COUNT(*) cantidad_transacciones,SUM(CASE WHEN tipo='DEBITO' THEN monto ELSE 0 END) total_debitos,SUM(CASE WHEN tipo='CREDITO' THEN monto ELSE 0 END) total_creditos,MAX(monto) monto_mayor FROM batch_datos WHERE conjunto=? AND proceso='transacciones' GROUP BY fecha ORDER BY fecha",conjunto);
    }
    @GetMapping("/intereses") public List<Map<String,Object>> intereses(@RequestParam(defaultValue="semana_1") String conjunto)  {
        return db.queryForList("SELECT registro_id cuenta_id,nombre,tipo,monto saldo_inicial,tasa,interes interes_mensual,monto+interes saldo_final FROM batch_datos WHERE conjunto=? AND proceso='intereses' ORDER BY registro_id",conjunto);
    }
    @GetMapping("/anuales") public List<Map<String,Object>> anuales(@RequestParam(defaultValue="semana_1") String conjunto)  {
        return db.queryForList("SELECT registro_id cuenta_id,EXTRACT(YEAR FROM fecha)::int anio,COUNT(*) cantidad_movimientos,SUM(CASE WHEN tipo='DEPOSITO' THEN monto ELSE 0 END) total_depositos,SUM(CASE WHEN tipo='RETIRO' THEN ABS(monto) ELSE 0 END) total_retiros,SUM(CASE WHEN tipo='COMPRA' THEN ABS(monto) ELSE 0 END) total_compras,SUM(monto) saldo_neto FROM batch_datos WHERE conjunto=? AND proceso='anuales' GROUP BY registro_id,EXTRACT(YEAR FROM fecha) ORDER BY registro_id,anio",conjunto);
    }
    @GetMapping("/rechazos") public List<Map<String,Object>> rechazos(@RequestParam(defaultValue="semana_1") String conjunto)  {
        return db.queryForList("SELECT proceso,fila,motivo,original FROM batch_rechazos WHERE conjunto=? ORDER BY proceso,fila",conjunto);
    }
}
