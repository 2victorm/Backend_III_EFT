# Despliegue en AWS

El proyecto se prepara para ejecutar Docker Compose en una instancia EC2. Este documento describe los pasos.  

## Crear la instancia

En la consola AWS, crear una instancia EC2 con Ubuntu Server 24.04 LTS de arquitectura x86_64. Para este conjunto de servicios usar al menos 8 GB de RAM y preferentemente 4 vCPU; ampliar si la compilación o las réplicas agotan memoria. Asignar un volumen de aproximadamente 30 GB o más para imágenes y datos. Estas son dimensiones de una demostración, no un dimensionamiento productivo.

Crear o seleccionar una clave SSH y guardar el `.pem`. Configurar el Security Group para permitir solo SSH, puerto 22, desde tu IP. No abrir PostgreSQL, Kafka, Eureka o Auth Server a Internet. La aplicación se utiliza a través de un túnel SSH y sus BFF con HTTPS.

Elegir región y tamaño según lo autorizado en la cuenta educativa. Revisar el costo que muestra AWS antes de crear recursos; no se presupone que la instancia esté cubierta por una capa gratuita.

## Instalar Docker y Compose

Conectar desde PowerShell, reemplazando la IP y el nombre de la clave:

```powershell
ssh -i .\clave-eft.pem ubuntu@IP_PUBLICA
```

En Ubuntu, instalar desde el repositorio oficial Docker:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git unzip
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

```bash
cat <<'EOF' | sudo tee /etc/apt/sources.list.d/docker.sources
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
EOF
```

```bash
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
exit
```

Volver a conectar por SSH para aplicar el grupo. Verificar `docker version` y `docker compose version`. Referencia oficial: https://docs.docker.com/engine/install/ubuntu/.

## Copiar y configurar el proyecto

Después de publicar el proyecto completo en GitHub, se puede clonar su URL real en EC2. Alternativamente, desde tu PC copiar el ZIP:

```powershell
scp -i .\clave-eft.pem .\Banco-XYZ-EFT.zip ubuntu@IP_PUBLICA:/home/ubuntu/
```

En la instancia:

```bash
unzip Banco-XYZ-EFT.zip
cd Banco-XYZ-EFT
cp .env.example .env
nano .env
```

Cambiar las contraseñas y los secretos de aplicaciones en `.env`; no publicarlo en GitHub. La configuración de Config Server y Eureka conserva las credenciales ficticias de semana 8: cambiarlas si se utiliza fuera de esta demostración privada. Las claves cliente de las pruebas deben coincidir con las configuradas.

El emisor OAuth sigue siendo `http://localhost:9000`, porque las pruebas se hacen a través del túnel al puerto local 9000. Los servicios obtienen las claves JWT por `http://auth-server:9000/oauth2/jwks` dentro de la red Docker.

## Construir y levantar

```bash
docker compose config --quiet
docker compose --parallel 2 build
docker compose up -d --wait --wait-timeout 600
docker compose ps
docker compose logs banco-batch
```

Comprobar que los tres jobs finalizaron y todos los contenedores siguen en ejecución. Si un build se interrumpe por falta de memoria, aumentar la instancia o construir con `--parallel 1`; no guardar una captura de fallo como si fuera un despliegue exitoso.

PostgreSQL y Kafka usan volúmenes Docker persistentes. En EC2 esos volúmenes viven en el disco de la instancia; no equivalen a una base de datos administrada. El schema SQL inicial se ejecuta una vez en un volumen PostgreSQL nuevo.

## Acceder desde Windows

Si la entrega local está activa, detener sus contenedores para liberar puertos. Abrir un terminal PowerShell y mantener este túnel activo:

```powershell
ssh -i .\clave-eft.pem -N -L 9000:127.0.0.1:9000 -L 8443:127.0.0.1:8443 -L 8444:127.0.0.1:8444 -L 8445:127.0.0.1:8445 -L 8761:127.0.0.1:8761 -L 8093:127.0.0.1:8093 ubuntu@IP_PUBLICA
```

En otro terminal, desde la carpeta local del proyecto, ejecutar `pruebas/Test-EFT.ps1` pasando los secretos configurados en EC2. Los mismos comandos de `instrucciones.md` funcionan con localhost, pero ahora conectan a los contenedores de AWS por el túnel cifrado. Eureka se abre en http://localhost:8761. No se requiere publicar los puertos de negocio ni cambiar certificados para esta demostración.

## Replicar los tres servicios de negocio

En EC2:

```bash
docker compose up -d --scale ms-clientes=2 --scale ms-cuentas=2 --scale ms-pagos=2 --wait --wait-timeout 600
docker compose ps
```

Esperar a que Eureka muestre dos instancias para MS-CLIENTES, MS-CUENTAS y MS-PAGOS. Repetir consultas y la prueba de operaciones desde tu PC. LoadBalancer usa Eureka y los UUID de instancia son únicos.

Esto acredita réplicas de contenedores para la demostración de escalabilidad horizontal de los microservicios. Es una ejecución en un solo host, no un despliegue multihost ni autoescalamiento de EC2. Mantener esa distinción en el informe y el video.


## Detener y conservar

Al terminar la demostración, ejecutar `docker compose down` para detener contenedores conservando los volúmenes y detener EC2 desde AWS. Los discos y otros recursos pueden seguir generando costos. Respaldar archivos y datos antes de terminar/eliminar la instancia o sus volúmenes. No eliminar recursos ajenos al proyecto.

## Mejoras futuras

Para uso real: claves de firma persistentes, certificado válido y HTTPS en Auth Server, autenticación de usuarios y permisos por cuenta, base administrada, varios brokers Kafka y orquestación multihost. Esas mejoras quedan fuera del alcance sencillo de esta entrega.
