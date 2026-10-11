# Ejecución y pruebas

## Preparación en Windows

Abrir Docker Desktop con contenedores Linux. El proyecto usa varias aplicaciones Java; asignar memoria suficiente a Docker (aproximadamente 6 a 8 GB para la demostración). Java se instala dentro de las imágenes: no necesitas cambiar tu Java local para ejecutar Compose.

Si los contenedores de semana 8 ocupan 9000, 8888 o 8761, detenerlos con `docker compose down` desde su carpeta. Extraer esta entrega en una carpeta corta, por ejemplo `C:\DUOC\Banco-XYZ-EFT`.

```powershell
cd C:\DUOC\Banco-XYZ-EFT
Copy-Item .env.example .env
docker compose config --quiet
docker compose --parallel 2 build
docker compose up -d --wait --wait-timeout 600
docker compose ps
```

PostgreSQL inicializa `banco_eft` con un cliente ficticio y las cuentas 101 y 102. No es necesario usar PostgreSQL de Windows. La carga Batch se realiza al iniciar banco-batch y el servidor queda disponible para consultar reportes.

## Pruebas automáticas de integración

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\Test-EFT.ps1
```

El script crea datos ficticios nuevos en cada ejecución. Verifica 401, 403 por canal y permiso, clientes, apertura y cierre, BFF web y móvil, depósitos, transferencias, pagos, retiros, idempotencia, saldo insuficiente, reportes Batch y evento Kafka. Las operaciones responden 202 y se consultan hasta alcanzar APLICADA o RECHAZADA. APLICADA describe el movimiento; el campo publicado informa si Kafka confirmó el evento.

Si cambiaste secretos en `.env`, pasarlos también al script:

```powershell
powershell -ExecutionPolicy Bypass -File .\pruebas\Test-EFT.ps1 -WebSecret 'TU_CLAVE_WEB' -MovilSecret 'TU_CLAVE_MOVIL' -CajeroSecret 'TU_CLAVE_CAJERO' -ConsultaSecret 'TU_CLAVE_CONSULTA'
```

## Pruebas manuales y evidencias

### Tokens por canal

```powershell
$r = curl.exe -sS -u 'web:web-demo-2026' -d 'grant_type=client_credentials' --data-urlencode 'scope=banco.read banco.write canal.web' http://localhost:9000/oauth2/token
$web = ($r | ConvertFrom-Json).access_token
$r = curl.exe -sS -u 'movil:movil-demo-2026' -d 'grant_type=client_credentials' --data-urlencode 'scope=banco.read banco.write canal.movil' http://localhost:9000/oauth2/token
$movil = ($r | ConvertFrom-Json).access_token
$r = curl.exe -sS -u 'cajero:cajero-demo-2026' -d 'grant_type=client_credentials' --data-urlencode 'scope=banco.read banco.write canal.cajero' http://localhost:9000/oauth2/token
$cajero = ($r | ConvertFrom-Json).access_token
```

El certificado es de prueba: `-k` se usa solo en la demostración local. No muestres tokens o secretos reales en capturas.

### Comparar BFF

```powershell
curl.exe -k -sS -H "Authorization: Bearer $web" https://localhost:8443/api/resumen/1
curl.exe -k -sS -H "Authorization: Bearer $movil" https://localhost:8444/api/resumen/1
curl.exe -k -sS -H "Authorization: Bearer $cajero" https://localhost:8445/api/cuentas/101/saldo
```

Web muestra cliente y cuentas completas; móvil solo id, saldo y estado. Cajero expone saldo y retiro, sin catálogo de clientes.

### Solicitar un retiro

Para evitar problemas de comillas en PowerShell se envía el JSON desde un archivo temporal:

```powershell
$clave = [guid]::NewGuid().ToString()
$archivo = Join-Path $env:TEMP 'retiro-eft.json'
'{"cuentaId":101,"monto":100}' | Set-Content -Encoding ASCII -NoNewline $archivo
curl.exe -k -i -X POST -H "Authorization: Bearer $cajero" -H "Idempotency-Key: $clave" -H 'Content-Type: application/json' --data-binary "@$archivo" https://localhost:8445/api/retiros
curl.exe -k -sS -H "Authorization: Bearer $cajero" "https://localhost:8445/api/pagos/$clave"
```

Consultar de nuevo unos segundos después. Repetir el POST con el mismo archivo y clave no cambia el saldo otra vez. Con otra clave y un monto que supere el saldo, la solicitud termina RECHAZADA.

### CRUD de clientes y cuentas

Por el BFF web se pueden ejecutar POST/PUT `/api/clientes`, POST `/api/cuentas`, PUT `/api/cuentas/{id}` para mantener el tipo, GET `/api/cuentas/{id}/saldo` y PUT `/api/cuentas/{id}/cerrar`. Ejemplos de cuerpos:

```json
{"rut":"TEST-123","nombre":"Cliente ejemplo","email":"ejemplo@example.com"}
```

```json
{"clienteId":1,"tipo":"AHORRO","saldo":0}
```

Para cerrar una cuenta, debe estar ACTIVA y tener saldo cero. Actualizar los datos de un cliente requiere PUT con rut, nombre y email.

### Batch

```powershell
docker compose logs banco-batch
curl.exe -k -sS -H "Authorization: Bearer $web" https://localhost:8443/api/batch/transacciones
curl.exe -k -sS -H "Authorization: Bearer $web" https://localhost:8443/api/batch/intereses
curl.exe -k -sS -H "Authorization: Bearer $web" https://localhost:8443/api/batch/anuales
curl.exe -k -sS -H "Authorization: Bearer $web" https://localhost:8443/api/batch/rechazos
```

Conteos esperados según las reglas verificadas con Java sobre los CSV originales:

| Conjunto | Proceso | Filas | Válidas finales | Inválidas | Cuentas repetidas |
|---|---|---:|---:|---:|---:|
| semana_1 | transacciones | 10 | 8 | 2 | 0 |
| semana_1 | intereses | 8 | 6 | 2 | 0 |
| semana_1 | anuales | 9 | 8 | 1 | 0 |
| semana_2 | transacciones | 10 | 7 | 3 | 0 |
| semana_2 | intereses | 8 | 5 | 3 | 0 |
| semana_2 | anuales | 9 | 8 | 1 | 0 |
| semana_3 | transacciones | 1000 | 392 | 608 | 0 |
| semana_3 | intereses | 1000 | 50 | 704 | 246 |
| semana_3 | anuales | 1000 | 746 | 254 | 0 |

Los contadores escritos del step de carga incluyen filas inválidas porque también se escriben en auditoría. Comparar los conteos finales con las tablas, no interpretar todos los escritos como filas financieras válidas.

```powershell
docker compose exec postgres psql -U banco -d banco_eft -c "SELECT conjunto,proceso,COUNT(*) FROM batch.batch_datos GROUP BY conjunto,proceso ORDER BY conjunto,proceso;"
docker compose exec postgres psql -U banco -d banco_eft -c "SELECT conjunto,proceso,COUNT(*) FROM batch.batch_rechazos GROUP BY conjunto,proceso ORDER BY conjunto,proceso;"
```

Para probar 1.000 registros por archivo, editar `.env`: `BATCH_CONJUNTO=semana_3`. Luego:

```powershell
docker compose up -d --force-recreate --wait --wait-timeout 600 banco-batch
curl.exe -k -sS -H "Authorization: Bearer $web" 'https://localhost:8443/api/batch/intereses?conjunto=semana_3'
```

Para repetir un conjunto ya completado, cambiar `BATCH_LOTE`, por ejemplo `eft-v2`, y recrear banco-batch. Repetir con el mismo conjunto/lote omite jobs completados. Para comparar rendimiento, ejecutar dos lotes distintos con BATCH_HILOS=1 y BATCH_HILOS=2 y medir la duración real en `batch.batch_job_execution`; no hay un porcentaje de mejora asumido.

Si tienes JDK instalado, puedes comprobar las reglas sin Docker:

```powershell
java .\pruebas\RunCsvTests.java .
```

### Resiliencia de cuentas

```powershell
docker compose stop ms-cuentas
```

Solicitar un retiro nuevo: responde 202 y se mantiene PENDIENTE. El saldo por BFF responde 503. Recuperar el servicio:

```powershell
docker compose start ms-cuentas
```

Esperar el registro de Eureka y cierre del circuito (puede demorar hasta un minuto), consultar la solicitud: debe llegar a APLICADA, con un solo descuento. Guardar capturas antes, durante y después.

### Recuperación de Kafka

```powershell
docker compose stop kafka
```

Solicitar un pago o retiro: cuentas puede aplicarlo, pero publicado sigue false. Recuperar Kafka:

```powershell
docker compose start kafka
docker compose logs --since 5m ms-pagos ms-notificaciones
```

El scheduler reenvía el evento pendiente. En auditoría debe existir un registro con el UUID del pago:

```powershell
curl.exe -sS -H "Authorization: Bearer $web" http://localhost:8093/api/eventos
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

### Alertas de seguridad

Un intento sin token a la API de pagos genera una alerta. Los servicios de negocio no se publican en el host; el intento se puede generar desde la red Docker:

```powershell
docker compose exec bff-web curl -i http://ms-pagos:8092/api/pagos
```

Respuesta esperada 401. Verificar `alertas-seguridad` en logs de ms-notificaciones o GET `/api/eventos`.

### Reinicio y conservación de datos

```powershell
docker compose restart banco-batch
```

Los jobs completados se omiten y los datos existentes se conservan. El historial está en `batch.batch_job_execution` y `batch.batch_step_execution`. Para probar un fallo durante procesamiento, usar semana_3 con BATCH_CHUNK=1, nuevo BATCH_LOTE y detener PostgreSQL mientras corre. Puede terminar antes del corte: solo presentar la prueba si efectivamente quedó FAILED. Recuperar PostgreSQL y banco-batch; comparar los conteos finales de todas las filas con la tabla anterior.

### Escalabilidad de los tres microservicios

```powershell
docker compose up -d --scale ms-clientes=2 --scale ms-cuentas=2 --scale ms-pagos=2 --wait --wait-timeout 600
docker compose ps
```

Abrir http://localhost:8761, usuario eureka y contraseña eureka2026. Comprobar dos instancias de cada servicio; repetir consultas y la prueba automática. Los UUID de instancias son distintos, no comparten una identidad fija en Eureka. Los pagos reintentados entre réplicas siguen protegidos por su UUID y la transacción de cuentas.

Volver a una instancia:

```powershell
docker compose up -d --scale ms-clientes=1 --scale ms-cuentas=1 --scale ms-pagos=1
```

## Detener

```powershell
docker compose down
```

Conserva los volúmenes. **No usar `down -v` para detener normalmente**: esa variante elimina los datos del proyecto EFT. Si necesitas repetir una demostración desde cero, respalda primero los datos que quieras conservar.

## Evidencias a guardar

Estado de Compose; tres jobs y sus resultados/rechazos; diferencias web/móvil/cajero; 401/403; movimientos y saldos; idempotencia; caída y recuperación; tópicos y auditoría; dos instancias por servicio en Eureka; y ejecución real en AWS. Estas evidencias alimentarán el informe y el video.
