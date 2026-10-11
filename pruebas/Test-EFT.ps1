param(
    [string]$WebSecret = 'web-demo-2026',
    [string]$MovilSecret = 'movil-demo-2026',
    [string]$CajeroSecret = 'cajero-demo-2026',
    [string]$ConsultaSecret = 'consulta-demo-2026'
)
$ErrorActionPreference = 'Stop'
$script:checks = 0
function Check($condition, [string]$message) {
    if (-not $condition) { throw "FALLO: $message" }
    $script:checks++
    Write-Host "OK: $message"
}
function Token([string]$client, [string]$secret, [string]$scope) {
    $raw = & curl.exe -sS -u "${client}:$secret" -d 'grant_type=client_credentials' --data-urlencode "scope=$scope" http://localhost:9000/oauth2/token
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo contactar Auth Server' }
    $r = ($raw -join "`n") | ConvertFrom-Json
    if (-not $r.access_token) { throw "No se obtuvo token para $client" }
    return $r.access_token
}
function Api([string]$method, [string]$url, [string]$token, $body = $null, [string]$key = '') {
    $curlArgs = @('-sS', '-k', '-X', $method, '-w', '\n%{http_code}', $url)
    if ($token) { $curlArgs += @('-H', "Authorization: Bearer $token") }
    if ($key) { $curlArgs += @('-H', "Idempotency-Key: $key") }
    $temp = $null
    try {
        if ($null -ne $body) {
            $temp = [IO.Path]::GetTempFileName()
            $json = $body | ConvertTo-Json -Compress -Depth 8
            [IO.File]::WriteAllText($temp, $json, (New-Object Text.UTF8Encoding($false)))
            $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$temp")
        }
        $raw = & curl.exe @curlArgs
        if ($LASTEXITCODE -ne 0) { throw "No se pudo contactar $url" }
        $lines = @($raw)
        $status = [int]$lines[-1]
        $content = if ($lines.Length -gt 1) { $lines[0..($lines.Length-2)] -join "`n" } else { '' }
        $parsed = $null
        try { $parsed = $content | ConvertFrom-Json } catch { $parsed = $content }
        return [pscustomobject]@{ Status = $status; Body = $parsed }
    } finally { if ($temp) { Remove-Item $temp -ErrorAction SilentlyContinue } }
}
function Completed([string]$id, [string]$token, [string]$state = 'APLICADA') {
    for ($i = 0; $i -lt 30; $i++) {
        $r = Api 'GET' "https://localhost:8443/api/pagos/$id" $token
        if ($r.Status -eq 200 -and $r.Body.estado -eq $state) { return $r }
        Start-Sleep -Seconds 2
    }
    throw "Pago $id no llego a $state en 60 segundos"
}
$web = Token 'web' $WebSecret 'banco.read banco.write canal.web'
$movil = Token 'movil' $MovilSecret 'banco.read banco.write canal.movil'
$cajero = Token 'cajero' $CajeroSecret 'banco.read banco.write canal.cajero'
$consulta = Token 'consulta' $ConsultaSecret 'banco.read canal.web'
Check ((Api 'GET' 'https://localhost:8443/api/cuentas' '').Status -eq 401) 'Sin token responde 401'
Check ((Api 'GET' 'https://localhost:8444/api/resumen/1' $web).Status -eq 403) 'Token web no ingresa al canal movil'
Check ((Api 'POST' 'https://localhost:8443/api/clientes' $consulta @{rut='prueba';nombre='Prueba';email='test@example.com'}).Status -eq 403) 'Cliente de consulta no puede escribir'
# Discovery puede tardar en actualizar la lista inicial de instancias.
for ($i=0; $i -lt 20; $i++) {
    $ready = Api 'GET' 'https://localhost:8443/api/cuentas' $web
    if ($ready.Status -eq 200) { break }
    Start-Sleep -Seconds 3
}
Check ($ready.Status -eq 200) 'BFF descubre el servicio de cuentas'
$unique = [guid]::NewGuid().ToString().Substring(0,8)
$client = Api 'POST' 'https://localhost:8443/api/clientes' $web @{rut="TEST-$unique";nombre='Cliente EFT';email='eft@example.com'}
Check ($client.Status -eq 201) 'Crear cliente'
$clienteId = $client.Body.id
$updated = Api 'PUT' "https://localhost:8443/api/clientes/$clienteId" $web @{rut="TEST-$unique";nombre='Cliente actualizado';email='eft@example.com'}
Check ($updated.Status -eq 200 -and $updated.Body.nombre -eq 'Cliente actualizado') 'Actualizar cliente'
$a = Api 'POST' 'https://localhost:8443/api/cuentas' $web @{clienteId=$clienteId;tipo='AHORRO';saldo=1000}
$b = Api 'POST' 'https://localhost:8443/api/cuentas' $web @{clienteId=$clienteId;tipo='AHORRO';saldo=200}
Check ($a.Status -eq 201 -and $b.Status -eq 201) 'Abrir dos cuentas'
$idA = $a.Body.id; $idB = $b.Body.id
$maintained = Api 'PUT' "https://localhost:8443/api/cuentas/$idA" $web @{tipo='AHORRO'}
Check ($maintained.Status -eq 200 -and $maintained.Body.tipo -eq 'AHORRO') 'Mantener datos de cuenta activa' 
$full = Api 'GET' "https://localhost:8443/api/resumen/$clienteId" $web
$light = Api 'GET' "https://localhost:8444/api/resumen/$clienteId" $movil
Check ($full.Status -eq 200 -and $null -ne $full.Body.cliente) 'Web devuelve cliente y cuentas'
Check ($light.Status -eq 200 -and $null -eq $light.Body.cliente -and @($light.Body.cuentas).Count -eq 2) 'Movil devuelve solo cuentas esenciales'
$key = [guid]::NewGuid().ToString()
$body = @{cuentaId=$idA;monto=100;tipo='DEPOSITO'}
$p = Api 'POST' 'https://localhost:8443/api/pagos' $web $body $key
Check ($p.Status -eq 202) 'Deposito registrado como solicitud'
$null = Completed $key $web
$again = Api 'POST' 'https://localhost:8443/api/pagos' $web $body $key
Check ($again.Status -eq 200) 'Repeticion devuelve la misma operacion'
$wrong = Api 'POST' 'https://localhost:8443/api/pagos' $web @{cuentaId=$idA;monto=101;tipo='DEPOSITO'} $key
Check ($wrong.Status -eq 409) 'Misma clave con datos diferentes responde 409'
$transferKey = [guid]::NewGuid().ToString()
$t = Api 'POST' 'https://localhost:8444/api/pagos' $movil @{cuentaId=$idA;destinoId=$idB;monto=50;tipo='TRANSFERENCIA'} $transferKey
Check ($t.Status -eq 202) 'Transferencia desde canal movil'
$null = Completed $transferKey $web
$payKey = [guid]::NewGuid().ToString()
$pay = Api 'POST' 'https://localhost:8443/api/pagos' $web @{cuentaId=$idA;monto=20;tipo='PAGO'} $payKey
Check ($pay.Status -eq 202) 'Pago registrado'
$null = Completed $payKey $web
$withdrawKey = [guid]::NewGuid().ToString()
$withdraw = Api 'POST' 'https://localhost:8445/api/retiros' $cajero @{cuentaId=$idA;monto=10} $withdrawKey
Check ($withdraw.Status -eq 202) 'Retiro desde BFF cajero'
$null = Completed $withdrawKey $web
$balanceA = Api 'GET' "https://localhost:8445/api/cuentas/$idA/saldo" $cajero
$balanceB = Api 'GET' "https://localhost:8443/api/cuentas/$idB/saldo" $web
Check ([decimal]$balanceA.Body.saldo -eq 1020 -and [decimal]$balanceB.Body.saldo -eq 250) 'Saldos correctos e idempotencia del deposito'
$rejectKey = [guid]::NewGuid().ToString()
$reject = Api 'POST' 'https://localhost:8445/api/retiros' $cajero @{cuentaId=$idA;monto=999999} $rejectKey
Check ($reject.Status -eq 202) 'Retiro sin saldo queda registrado para validacion'
$null = Completed $rejectKey $web 'RECHAZADA'
$balanceAfter = Api 'GET' "https://localhost:8445/api/cuentas/$idA/saldo" $cajero
Check ([decimal]$balanceAfter.Body.saldo -eq 1020) 'Solicitud rechazada no cambia el saldo'
Check ((Api 'PUT' "https://localhost:8443/api/cuentas/$idA/cerrar" $web).Status -eq 409) 'No se cierra una cuenta con fondos'
$empty = Api 'POST' 'https://localhost:8443/api/cuentas' $web @{clienteId=$clienteId;tipo='AHORRO';saldo=0}
Check ($empty.Status -eq 201) 'Abrir cuenta vacia'
$closed = Api 'PUT' "https://localhost:8443/api/cuentas/$($empty.Body.id)/cerrar" $web
Check ($closed.Status -eq 200 -and $closed.Body.estado -eq 'CERRADA') 'Cerrar cuenta vacia'
foreach ($report in @('transacciones','intereses','anuales','rechazos')) {
    $r = Api 'GET' "https://localhost:8443/api/batch/$report" $web
    Check ($r.Status -eq 200 -and @($r.Body).Count -gt 0) "Reporte Batch $report disponible"
}
for ($i=0; $i -lt 30; $i++) {
    $events = Api 'GET' 'http://localhost:8093/api/eventos' $web
    if (@($events.Body | Where-Object { $_.evento_id -eq $key }).Count -eq 1) { break }
    Start-Sleep -Seconds 2
}
Check (@($events.Body | Where-Object { $_.evento_id -eq $key }).Count -eq 1) 'Kafka entrega el evento y auditoria no lo duplica'
Write-Host "PRUEBAS COMPLETADAS: $script:checks comprobaciones. Cliente $clienteId; cuentas $idA y $idB."
