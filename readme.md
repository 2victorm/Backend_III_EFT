# Banco XYZ — Evaluación Final Transversal

**Asignatura:** Desarrollo Backend III — PBY2203  
**Estudiante:** Víctor Marambio  
**Evaluación:** Semana 9

## Descripción

Solución backend para la modernización del Banco XYZ, desarrollada
con Spring Batch, Spring Cloud y microservicios.

El proyecto incluye procesamiento de archivos CSV, gestión de clientes
y cuentas, operaciones bancarias y acceso mediante tres Backend for
Frontend para los canales web, móvil y cajero.


## Tecnologías utilizadas

- Java 21 y Spring Boot.
- Spring Batch.
- Spring Cloud Config, Eureka y LoadBalancer.
- Spring Security con OAuth 2.0 y JWT.
- Resilience4j.
- PostgreSQL y JDBC.
- Apache Kafka.
- Docker Compose.

## Componentes principales

| Componente | Función |
|---|---|
| banco-batch | Procesamiento de transacciones, intereses y estados anuales |
| bff-web | Gestión y consultas completas |
| bff-movil | Consultas con respuestas reducidas |
| bff-cajero | Consulta de saldo y retiros |
| ms-clientes | Gestión de clientes |
| ms-cuentas | Apertura, mantenimiento, cierre y movimientos de cuentas |
| ms-pagos | Procesamiento de depósitos, pagos, transferencias y retiros |
| ms-notificaciones | Recepción y registro de eventos |
| auth-server | Emisión de tokens de acceso |
| config-server | Configuración centralizada |
| discovery-server | Registro y descubrimiento de servicios |

## Ejecución

Con Docker Desktop iniciado, abrir PowerShell en la carpeta del proyecto.

En la primera ejecución, crear el archivo de configuración:

```powershell
Copy-Item .env.example .env
```

Construir e iniciar los contenedores:

```powershell
docker compose --parallel 2 build
docker compose up -d --wait --wait-timeout 600
docker compose ps
```

Las pruebas se realizan mediante Postman, utilizando tokens de acceso
para cada canal.

## Documentación

- [Instrucciones de ejecución y pruebas](instrucciones.md)
- [Procedimiento de despliegue en AWS](despliegue.md)
- Informe técnico PDF con arquitectura y evidencias.
- Video de presentación y demostración.

## Resultados

Las pruebas realizadas en Docker Desktop sobre Windows verificaron
el procesamiento batch, la seguridad de los endpoints, las operaciones
bancarias, la idempotencia de los retiros y la recepción de eventos
mediante Kafka.

También se comprobó la recuperación de consultas ante una caída
de servicio y el registro de réplicas en Eureka.