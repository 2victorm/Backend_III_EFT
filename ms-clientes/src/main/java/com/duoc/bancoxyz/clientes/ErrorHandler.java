package com.duoc.bancoxyz.clientes;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;
@RestControllerAdvice public class ErrorHandler  {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e)  {
        return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<?> duplicate()  {
        return ResponseEntity.status(409).body(Map.of("error","El registro ya existe"));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) ResponseEntity<?> database(org.springframework.dao.DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error","Base de datos temporalmente no disponible"));
    }
    @ExceptionHandler(ServicioNoDisponible.class) ResponseEntity<?> unavailable(ServicioNoDisponible e)  {
        return ResponseEntity.status(503).body(Map.of("error",e.getMessage()));
    }
}
class ServicioNoDisponible extends RuntimeException  {
    ServicioNoDisponible(String s)  {
        super(s);
    }
}
