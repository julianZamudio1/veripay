# Instalación, configuración y despliegue

## Requisitos

| Herramienta | Versión probada |
|---|---|
| JDK | 17 (Microsoft Build of OpenJDK 17.0.17) |
| Maven | 3.9 |
| Node.js y npm | Node 24, npm 11 |
| Eclipse | Eclipse IDE for Enterprise Java and Web Developers |
| Opcional | Docker (PostgreSQL 16), WildFly 31+ o JBoss EAP 8 |

## Perfiles de Spring

| Perfil | Base de datos | Secretos | Datos demo | Uso |
|---|---|---|---|---|
| `dev` (por defecto) | H2 en archivo `./data/veripay` | De desarrollo, incluidos | Sí | Trabajo local y Eclipse |
| `dev,postgres` | PostgreSQL | De desarrollo | Sí | Probar con PostgreSQL en local |
| `postgres` | PostgreSQL | Variables de entorno | No | Producción con Tomcat embebido |
| `jboss` | DataSource JNDI `java:jboss/datasources/VeriPayDS` | Variables de entorno | No | Producción en WildFly o JBoss EAP |
| `test` | H2 en memoria | De prueba | No | Pruebas automatizadas |

Si activas un perfil distinto de `dev` sin `VERIPAY_JWT_SECRETO`, la aplicación se detiene al arrancar y te dice qué variable falta.

## Backend en Eclipse

1. `File → Import → Maven → Existing Maven Projects`.
2. En *Root Directory* elige `veripay/backend` y pulsa *Finish*.
3. Clic derecho en `VeriPayApplication.java` → `Run As → Java Application`.
4. Para las pruebas: clic derecho en el proyecto → `Run As → JUnit Test`.

| URL | Qué hay |
|---|---|
| http://localhost:8080/veripay/api/v1 | API |
| http://localhost:8080/veripay/swagger-ui.html | Documentación interactiva |
| http://localhost:8080/veripay/h2-console | Consola de H2 (solo `dev`). JDBC URL `jdbc:h2:file:./data/veripay`, usuario `sa`, sin contraseña. |

Usuarios demo del perfil `dev`: `admin / Admin123!`, `analista / Analista123!`, `auditor / Auditor123!`.

## Frontend

```bash
cd frontend
npm install
npm start
```

Abre http://localhost:4200. Las llamadas a `/api` van al backend en el puerto 8080 mediante `proxy.conf.json`, así que el backend debe estar corriendo.

Para editar TypeScript en Eclipse instala **Wild Web Developer** desde el Eclipse Marketplace.

## Un solo WAR con frontend y backend

```bash
cd backend
mvn -Pfull clean package
java -jar target/veripay.war
```

El perfil Maven `full` ejecuta `npm install` y `npm run build` y copia el resultado a `static/` dentro del WAR. Abre http://localhost:8080/veripay/.

## PostgreSQL

```bash
docker compose up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev,postgres
```

Flyway crea las tablas en el primer arranque. Para producción usa solo `postgres` y define las variables de [seguridad.md](seguridad.md#secretos-y-configuración).

## WildFly o JBoss EAP

Probado con **WildFly 41.0.1.Final, distribución Jakarta EE 10** (`wildfly-ee-10-41.0.1.Final.zip` en las [releases de WildFly](https://github.com/wildfly/wildfly/releases)). Usa la variante `ee-10`: Spring Boot 3.3 trabaja con Servlet 6.0. El resultado de cada prueba está en [auditoria.md](auditoria.md#verificación-en-wildfly).

**Prueba rápida con H2:**

1. Descomprime WildFly, por ejemplo en `C:\Users\<tú>\wildfly`.
2. Genera el WAR: `mvn -Pfull clean package` en `backend/`.
3. Inicia el servidor. Si el puerto 8080 está ocupado (por ejemplo, por la app corriendo en Eclipse), suma 100 a todos los puertos:

   ```bash
   bin\standalone.bat -Djboss.socket.binding.port-offset=100
   ```

4. Copia `backend/target/veripay.war` a `standalone/deployments/`. WildFly crea `veripay.war.deployed` si todo sale bien, o `veripay.war.failed` con el error en `standalone/log/server.log`.
5. Abre http://localhost:8080/veripay/ (o http://localhost:8180/veripay/ con el desplazamiento de puertos).

La base H2 queda en `data/veripay.mv.db`, dentro de la carpeta desde la que arrancaste WildFly.

**Con PostgreSQL y DataSource del servidor:**

1. Levanta la base: `docker compose up -d`.
2. Despliega el driver JDBC: `bin/jboss-cli.bat --connect --command="deploy postgresql-42.7.4.jar"`.
3. Crea el DataSource: `bin/jboss-cli.bat --connect --file=backend/wildfly/configurar-datasource.cli`.
4. Define `VERIPAY_JWT_SECRETO` y `VERIPAY_ADMIN_PASSWORD` en el entorno del servidor.
5. Arranca con el perfil: `bin/standalone.bat -Dspring.profiles.active=jboss`.
6. Despliega el WAR.

**Desde Eclipse:** instala *JBoss Tools* desde el Marketplace. En la vista `Servers` crea un servidor *WildFly* que apunte a la carpeta descomprimida, agrega el proyecto `veripay` e inícialo.

### Por qué `jboss-deployment-structure.xml` excluye subsistemas

Spring Boot trae su propio logging, JPA (Hibernate), validación y capa web. El archivo desactiva los equivalentes de WildFly para que no choquen:

| Subsistema | Motivo |
|---|---|
| `logging` | Spring usa Logback; el de WildFly duplicaría la configuración |
| `jpa` | Spring crea el `EntityManagerFactory`; WildFly intentaría crear otro |
| `jaxrs` | Los endpoints son de Spring MVC, no de RESTEasy |
| `weld` | Spring gestiona los beans; CDI no hace falta |
| `jsf` | Jakarta Faces exige CDI al arrancar. Sin esta exclusión el despliegue falla con `CDI is not available` |

Al desplegar verás avisos `WFLYSRV0274` y `WFLYEE0007`. Son esperados y no afectan el funcionamiento (detalle en [auditoria.md](auditoria.md#pendientes)).

## Pruebas

```bash
cd backend
mvn test
```

50 pruebas: validación de CURP y CLABE, comparador biométrico (incluida la imagen bomba), reglas del KYC, transacciones (concurrencia con 8 hilos e idempotencia) y el contrato HTTP del API (códigos, cabeceras y formato de error).

```bash
cd frontend
npm test
```

5 pruebas del validador de CURP. Karma necesita Chrome; con Edge define `CHROME_BIN` con la ruta de `msedge.exe`.

## Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `Port 8080 was already in use` | Otra instancia corre en ese puerto (por ejemplo, la de Eclipse) | Detén la otra o usa `--server.port=8081` |
| `Define la variable de entorno VERIPAY_JWT_SECRETO…` | Perfil distinto de `dev` sin secreto | Define la variable o usa `dev` en local |
| `insufficient memory` / `errno=1455` al compilar o probar | Windows sin memoria virtual (Eclipse, WSL, bases de datos y el build al mismo tiempo) | Cierra procesos o limita la memoria: `MAVEN_OPTS=-Xmx384m mvn test -DargLine=-Xmx512m` |
| El frontend responde 404 al recargar `/clientes/5` en el WAR | La ruta no está en `SpaForwardController` | Agrega la ruta nueva a la lista del controlador |
| `veripay.war.failed` con `CDI is not available` | Un `jboss-deployment-structure.xml` sin la exclusión de `jsf` | Usa el del repositorio, que excluye `jsf` |
| La app no toma cambios del código en Eclipse | Sigue corriendo una instancia vieja en otra pestaña de la consola | Detén todas las instancias (cuadro rojo) antes de ejecutar de nuevo |
| `ng test` queda abierto tras "TOTAL: 5 SUCCESS" | Karma con Edge en Windows | Cierra con Ctrl+C; los resultados ya son válidos |
