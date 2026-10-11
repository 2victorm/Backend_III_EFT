package com.duoc.bancoxyz.batch;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
@Component public class BatchRunner implements CommandLineRunner  {
    private final List<Job> jobs;
    private final JobLauncher launcher;
    private final String conjunto;
    private final String lote;
    public BatchRunner(List<Job> jobs,JobLauncher launcher,@Value("${batch.conjunto}") String conjunto,@Value("${batch.lote}") String lote)  {
        this.jobs=jobs;
        this.launcher=launcher;
        this.conjunto=conjunto;
        this.lote=lote;
    }
    @Override public void run(String... args)throws Exception  {
        if(!Set.of("semana_1","semana_2","semana_3").contains(conjunto))throw new IllegalArgumentException("Conjunto no valido");
        var parametros=new JobParametersBuilder().addString("conjunto",conjunto).addString("lote",lote).toJobParameters();
        for(Job job:jobs)  {
            boolean terminado=false;
            for(int intento=1;intento<=3;intento++)  {
                try  {
                    var e=launcher.run(job,parametros);
                    System.out.println(job.getName()+": "+e.getStatus());
                    for(var s:e.getStepExecutions())System.out.println(s.getStepName()+" leidos="+s.getReadCount()+" escritos="+s.getWriteCount());
                    if(e.getStatus()==BatchStatus.COMPLETED)  {
                        terminado=true;
                        break;
                    }
                }
                catch(JobInstanceAlreadyCompleteException e)  {
                    System.out.println(job.getName()+": ya completado, no se repite");
                    terminado=true;
                    break;
                }
                catch(Exception e)  {
                    System.err.println("Intento "+intento+" fallo: "+e.getClass().getSimpleName());
                }
                if(intento<3)Thread.sleep(5000);
            }
            if(!terminado)throw new IllegalStateException("Job fallido despues de 3 intentos: "+job.getName());
        }
    }
}
