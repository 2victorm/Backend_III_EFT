package com.duoc.bancoxyz.batch;
import java.math.BigDecimal;
import java.time.LocalDate;
public record DatoProcesado(int fila,String original,Long id,LocalDate fecha,BigDecimal monto,String tipo,String nombre,Integer edad,String descripcion,BigDecimal tasa,BigDecimal interes,String error)  {
}
