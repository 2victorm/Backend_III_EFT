package com.duoc.bancoxyz.batch;
import java.math.BigDecimal;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration public class BatchJobs  {
    private final JobRepository repo;
    private final PlatformTransactionManager tx;
    private final JdbcTemplate db;
    private final String conjunto;
    private final BigDecimal ahorro,prestamo;
    private final int chunk;
    public BatchJobs(JobRepository repo,PlatformTransactionManager tx,JdbcTemplate db,@Value("${batch.conjunto}") String conjunto,@Value("${banco.tasa.ahorro}") BigDecimal ahorro,@Value("${banco.tasa.prestamo}") BigDecimal prestamo,@Value("${batch.chunk:100}") int chunk)  {
        this.repo=repo;
        this.tx=tx;
        this.db=db;
        this.conjunto=conjunto;
        this.ahorro=ahorro;
        this.prestamo=prestamo;
        this.chunk=chunk;
    }
    // Configura los hilos utilizados para procesar los datos por lotes.
    @Bean ThreadPoolTaskExecutor batchExecutor(@Value("${batch.hilos:2}") int hilos)  {
        var e=new ThreadPoolTaskExecutor();
        e.setCorePoolSize(hilos);
        e.setMaxPoolSize(hilos);
        e.setQueueCapacity(hilos*2);
	e.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        e.setThreadNamePrefix("batch-");
        e.initialize();
        return e;
    }
    @Bean @StepScope FlatFileItemReader<Fila> transaccionesReader(@Value("${batch.ruta}") String ruta)  {
        return reader(ruta,"movimientos_financieros_diarios.csv");
    }
    @Bean @StepScope FlatFileItemReader<Fila> interesesReader(@Value("${batch.ruta}") String ruta)  {
        return reader(ruta,"intereses_trimestrales.csv");
    }
    @Bean @StepScope FlatFileItemReader<Fila> anualesReader(@Value("${batch.ruta}") String ruta)  {
        return reader(ruta,"estados_financieros_anuales.csv");
    }
    // Lee el archivo CSV del conjunto seleccionado y omite la cabecera.
    private FlatFileItemReader<Fila> reader(String ruta,String archivo)  {
        return new FlatFileItemReaderBuilder<Fila>().name(archivo).resource(new FileSystemResource(ruta+"/"+conjunto+"/"+archivo)).encoding("UTF-8").linesToSkip(1).saveState(false).lineMapper((linea,num)->new Fila(num,linea)).build();
    }
    // Guarda las filas válidas y registra las inválidas con su motivo.
    // Actualiza los registros existentes para evitar duplicarlos al releer el archivo.
    private Step carga(String proceso,FlatFileItemReader<Fila> reader,ThreadPoolTaskExecutor executor)  {
        return new ChunkOrientedStepBuilder<Fila,DatoProcesado>("cargar-"+proceso,repo,chunk).reader(reader) .processor(f->ValidadorCsv.procesar(f,proceso,ahorro,prestamo)).writer(items->  {
            for(var d:items)guardar(proceso,d);
        }
        ) .transactionManager(tx).taskExecutor(executor).faultTolerant().retry(TransientDataAccessException.class).retryLimit(3).build();
    }
    private void guardar(String proceso,DatoProcesado d)  {
        if(d.error()!=null)  {
            db.update("INSERT INTO batch_rechazos(conjunto,proceso,fila,motivo,original) VALUES(?,?,?,?,?) ON CONFLICT(conjunto,proceso,fila) DO UPDATE SET motivo=EXCLUDED.motivo,original=EXCLUDED.original",conjunto,proceso,d.fila(),d.error(),d.original());
            return;
        }
        // La fila del archivo identifica un movimiento: dos operaciones iguales pueden ser legitimas.
        db.update("INSERT INTO batch_datos(conjunto,proceso,fila,registro_id,fecha,monto,tipo,nombre,edad,descripcion,tasa,interes,original) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(conjunto,proceso,fila) DO UPDATE SET registro_id=EXCLUDED.registro_id,fecha=EXCLUDED.fecha,monto=EXCLUDED.monto,tipo=EXCLUDED.tipo,nombre=EXCLUDED.nombre,edad=EXCLUDED.edad,descripcion=EXCLUDED.descripcion,tasa=EXCLUDED.tasa,interes=EXCLUDED.interes,original=EXCLUDED.original",conjunto,proceso,d.fila(),d.id(),d.fecha(),d.monto(),d.tipo(),d.nombre(),d.edad(),d.descripcion(),d.tasa(),d.interes(),d.original());
    }
    private Step preparar(String proceso)  {
        Tasklet t=(contribution,context)->  {
            db.update("DELETE FROM batch_datos WHERE conjunto=? AND proceso=?",conjunto,proceso);
            db.update("DELETE FROM batch_rechazos WHERE conjunto=? AND proceso=?",conjunto,proceso);
            return RepeatStatus.FINISHED;
        }
        ;
        return new StepBuilder("preparar-"+proceso,repo).tasklet(t,tx).build();
    }
    // Conserva la primera fila válida de cada cuenta y rechaza las repetidas.
    private Step duplicadosIntereses()  {
        Tasklet t=(contribution,context)->  {
            // Para una cuenta solo se calcula una vez. Conservamos la primera fila valida, de forma determinista.
            db.update("INSERT INTO batch_rechazos(conjunto,proceso,fila,motivo,original) SELECT conjunto,proceso,fila,'Cuenta repetida en archivo de intereses',original FROM batch_datos d WHERE conjunto=? AND proceso='intereses' AND fila>(SELECT MIN(fila) FROM batch_datos x WHERE x.conjunto=d.conjunto AND x.proceso=d.proceso AND x.registro_id=d.registro_id) ON CONFLICT(conjunto,proceso,fila) DO NOTHING",conjunto);
            db.update("DELETE FROM batch_datos d WHERE conjunto=? AND proceso='intereses' AND EXISTS(SELECT 1 FROM batch_rechazos r WHERE r.conjunto=d.conjunto AND r.proceso=d.proceso AND r.fila=d.fila)",conjunto);
            return RepeatStatus.FINISHED;
        }
        ;
        return new StepBuilder("deduplicar-intereses",repo).tasklet(t,tx).build();
    }
    @Bean Job reporteTransaccionesDiariasJob(FlatFileItemReader<Fila> transaccionesReader,ThreadPoolTaskExecutor batchExecutor)  {
        return new JobBuilder("reporteTransaccionesDiariasJob",repo).start(preparar("transacciones")).next(carga("transacciones",transaccionesReader,batchExecutor)).build();
    }
    @Bean Job calculoInteresesMensualesJob(FlatFileItemReader<Fila> interesesReader,ThreadPoolTaskExecutor batchExecutor)  {
        return new JobBuilder("calculoInteresesMensualesJob",repo).start(preparar("intereses")).next(carga("intereses",interesesReader,batchExecutor)).next(duplicadosIntereses()).build();
    }
    @Bean Job estadosCuentaAnualesJob(FlatFileItemReader<Fila> anualesReader,ThreadPoolTaskExecutor batchExecutor)  {
        return new JobBuilder("estadosCuentaAnualesJob",repo).start(preparar("anuales")).next(carga("anuales",anualesReader,batchExecutor)).build();
    }
}
