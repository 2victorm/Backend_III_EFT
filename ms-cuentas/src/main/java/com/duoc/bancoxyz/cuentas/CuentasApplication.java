package com.duoc.bancoxyz.cuentas;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling public class CuentasApplication  {
    public static void main(String[] args)  {
        SpringApplication.run(CuentasApplication.class,args);
    }
}
