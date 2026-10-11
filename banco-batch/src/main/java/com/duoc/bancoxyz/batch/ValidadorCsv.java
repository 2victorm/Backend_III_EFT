package com.duoc.bancoxyz.batch;
import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
// Sin estado mutable: se puede usar desde los hilos de los tres jobs.
public class ValidadorCsv  {
    public static LocalDate fecha(String valor)  {
        for(String formato:List.of("uuuu-MM-dd","uuuu/MM/dd","dd-MM-uuuu","dd/MM/uuuu"))  {
            try  {
                return LocalDate.parse(valor.trim(),DateTimeFormatter.ofPattern(formato).withResolverStyle(ResolverStyle.STRICT));
            }
            catch(DateTimeParseException ignored)  {
            }
        }
        throw new IllegalArgumentException("Fecha invalida");
    }
    public static DatoProcesado procesar(Fila fila,String proceso,BigDecimal ahorro,BigDecimal prestamo)  {
        try  {
            String[] f=fila.texto().split(",",-1);
            for(int i=0;i<f.length;i++)f[i]=f[i].trim();
            if(f.length!=(proceso.equals("transacciones")?4:5))throw new IllegalArgumentException("Cantidad de columnas invalida");
            long id=Long.parseLong(f[0]);
            if(id<=0)throw new IllegalArgumentException("Identificador invalido");
            if(proceso.equals("intereses"))  {
                BigDecimal saldo=new BigDecimal(f[2]);
                int edad=Integer.parseInt(f[3]);
                String tipo=f[4].toLowerCase(Locale.ROOT);
                if(saldo.signum()<=0 || saldo.scale()>2 || saldo.precision()>15)throw new IllegalArgumentException("Saldo invalido");
                if(edad<18||edad>100)throw new IllegalArgumentException("Edad fuera de rango");
                if(f[1].isBlank()||f[1].length()>150)throw new IllegalArgumentException("Nombre invalido");
                BigDecimal tasa=switch(tipo)  {
                    case "ahorro"->ahorro;
                    case "prestamo"->prestamo;
                    default->throw new IllegalArgumentException("Tipo de cuenta desconocido");
                }
                ;
                BigDecimal interes=saldo.multiply(tasa).setScale(2,RoundingMode.HALF_UP);
                return new DatoProcesado(fila.numero(),fila.texto(),id,null,saldo,tipo,f[1],edad,null,tasa,interes,null);
            }
            LocalDate fecha=fecha(f[1]);
            BigDecimal monto=new BigDecimal(f[proceso.equals("transacciones")?2:3]);
            String tipo=f[proceso.equals("transacciones")?3:2].toUpperCase(Locale.ROOT);
            if(monto.signum()==0 || monto.scale()>2 || monto.precision()>15)throw new IllegalArgumentException("Monto invalido");
            if(proceso.equals("transacciones"))  {
                if(monto.signum()<0)throw new IllegalArgumentException("Monto negativo");
                if(!Set.of("DEBITO","CREDITO").contains(tipo))throw new IllegalArgumentException("Tipo de transaccion desconocido");
                return new DatoProcesado(fila.numero(),fila.texto(),id,fecha,monto,tipo,null,null,null,null,null,null);
            }
            if(!Set.of("DEPOSITO","RETIRO","COMPRA").contains(tipo))throw new IllegalArgumentException("Tipo de movimiento desconocido");
            String descripcion=f[4].isBlank()?"SIN DESCRIPCION":f[4];
            if(descripcion.length()>250)throw new IllegalArgumentException("Descripcion demasiado larga");
            monto=tipo.equals("DEPOSITO")?monto.abs():monto.abs().negate();
            return new DatoProcesado(fila.numero(),fila.texto(),id,fecha,monto,tipo,null,null,descripcion,null,null,null);
        }
        catch(IllegalArgumentException e)  {
            return new DatoProcesado(fila.numero(),fila.texto(),null,null,null,null,null,null,null,null,null,e.getMessage() == null ? "Campo numerico vacio o invalido" : e.getMessage());
        }
    }
}
