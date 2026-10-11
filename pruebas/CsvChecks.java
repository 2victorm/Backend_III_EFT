package com.duoc.bancoxyz.batch;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
public class CsvChecks  {
    private static int checks;
    static void check(boolean ok, String msg)  {
        checks++;
        if (!ok) throw new AssertionError(msg);
    }
    static DatoProcesado parse(String line, String proceso)  {
        return ValidadorCsv.procesar(new Fila(2, line), proceso, new BigDecimal("0.005"), new BigDecimal("0.015"));
    }
    public static void main(String[] args) throws Exception  {
        check(parse("101,Ana,5000,30,ahorro", "intereses").interes().compareTo(new BigDecimal("25.00")) == 0, "Interes mensual ahorro");
        check(parse("101,Ana,5000,30,prestamo", "intereses").interes().compareTo(new BigDecimal("75.00")) == 0, "Interes mensual prestamo");
        check(parse("101,Ana,,30,ahorro", "intereses").error() != null, "Saldo ausente");
        check(parse("101,Ana,5000,,ahorro", "intereses").error() != null, "Edad ausente");
        check(parse("101,Ana,5000,17,ahorro", "intereses").error() != null, "Edad invalida");
        check(parse("101,Ana,5000,30,hipoteca", "intereses").error() != null, "Tipo desconocido");
        check(parse("1,2024-01-01,-200,debito", "transacciones").error() != null, "Monto negativo");
        check(parse("1,2024-01-01,0,debito", "transacciones").error() != null, "Monto cero");
        check(parse("1,2024-01-01,1.001,debito", "transacciones").error() != null, "Precision monetaria");
        check(parse("1,2024-02-30,100,debito", "transacciones").error() != null, "Fecha imposible");
        check(parse("1,2024-01-01,100,desconocido", "transacciones").error() != null, "Transaccion desconocida");
        check(parse("1,2024-01-01,100", "transacciones").error() != null, "Fila incompleta");
        check(parse("101,2024-01-01,retiro,500,", "anuales").monto().compareTo(new BigDecimal("-500")) == 0, "Signo retiro");
        check(parse("101,2024-01-01,deposito,-500,", "anuales").monto().compareTo(new BigDecimal("500")) == 0, "Signo deposito");
        check(parse("101,2024-01-01,compra,500,", "anuales").descripcion().equals("SIN DESCRIPCION"), "Descripcion vacia");
        for (String fecha : List.of("2024-03-22", "2024/03/22", "22-03-2024", "22/03/2024")) check(ValidadorCsv.fecha(fecha).equals(LocalDate.of(2024,3,22)), "Formato fecha " + fecha);
        String[][] tipos =  {
            {
                "transacciones","movimientos_financieros_diarios.csv"
            }
            ,  {
                "intereses","intereses_trimestrales.csv"
            }
            ,  {
                "anuales","estados_financieros_anuales.csv"
            }
        }
        ;
        for (String conjunto : List.of("semana_1", "semana_2", "semana_3"))  {
            for (String[] tipo : tipos)  {
                var lines = Files.readAllLines(Path.of(args[0], "datos", conjunto, tipo[1]));
                var filas = new ArrayList<Fila>();
                for (int i=1; i<lines.size(); i++) filas.add(new Fila(i+1,lines.get(i)));
                var sec = filas.stream().map(f->ValidadorCsv.procesar(f,tipo[0],new BigDecimal("0.005"),new BigDecimal("0.015"))).toList();
                var par = filas.parallelStream().map(f->ValidadorCsv.procesar(f,tipo[0],new BigDecimal("0.005"),new BigDecimal("0.015"))).toList();
                check(sec.equals(par), "Resultado identico en paralelo: " + conjunto + "/" + tipo[0]);
                long validos = sec.stream().filter(d->d.error()==null).count();
                long rechazados = sec.size()-validos;
                long duplicados = 0;
                if (tipo[0].equals("intereses"))  {
                    long unicos=sec.stream().filter(d->d.error()==null).map(DatoProcesado::id).distinct().count();
                    duplicados=validos-unicos;
                    validos=unicos;
                }
                check(validos+rechazados+duplicados==filas.size(), "Todas las filas contabilizadas");
                System.out.printf("%s,%s,filas=%d,validos=%d,invalidos=%d,duplicados=%d%n", conjunto,tipo[0],filas.size(),validos,rechazados,duplicados);
            }
        }
        System.out.println("PASS: " + checks + " comprobaciones de reglas CSV y procesamiento paralelo");
    }
}
