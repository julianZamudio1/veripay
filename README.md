# VeriPay — Onboarding con validación de identidad y pagos electrónicos

Proyecto de portafolio full-stack que integra **Java, Spring, JBoss/WildFly, SQL, Angular, HTML, CSS y JavaScript/TypeScript**, desarrollado en **Eclipse**.

El dominio replica lo que hace una empresa de soluciones de TI para **validación de identidades, análisis biométrico y medios de pago electrónicos**:

1. **Alta de clientes (KYC)**: validación real de CURP (formato RENAPO, entidad, fecha y dígito verificador), RFC, mayoría de edad y consistencia fecha ↔ CURP.
2. **Verificación biométrica**: se compara la foto de la identificación contra una selfie y se obtiene un puntaje contra un umbral configurable. Las imágenes **no se guardan**, solo su huella SHA-256.
3. **Cuentas con CLABE**: generación de CLABE de 18 dígitos con dígito de control (algoritmo Banxico 3-7-1). Solo clientes verificados pueden abrir cuenta.
4. **Pagos**: depósitos, retiros y transferencias con **bloqueo pesimista ordenado** (sin interbloqueos), **idempotencia** (`Idempotency-Key`) y límite por operación.
5. **Seguridad y auditoría**: JWT, roles (ADMIN / ANALISTA / AUDITOR) y bitácora de cada operación.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation), Hibernate |
| Seguridad | Spring Security + OAuth2 Resource Server, JWT HS256, BCrypt |
| Base de datos | SQL con migraciones **Flyway**; H2 (dev) y **PostgreSQL** (prod) |
| Servidor | **WAR** desplegable en **JBoss EAP / WildFly** o ejecutable con Tomcat embebido |
| Frontend | **Angular 20** (standalone components, signals, reactive forms, interceptores, guards), HTML, CSS |
| Documentación API | OpenAPI / Swagger UI |
| Pruebas | JUnit 5, AssertJ, MockMvc, Spring Security Test, prueba de concurrencia |
| IDE | Eclipse IDE for Enterprise Java and Web Developers |

## Estructura

```
veripay/
├── backend/                      Proyecto Maven (importar en Eclipse)
│   ├── src/main/java/com/veripay/
│   │   ├── auth/                 Login y emisión de JWT
│   │   ├── cliente/              Clientes, validación de CURP
│   │   ├── biometria/            Verificación KYC (ComparadorBiometrico intercambiable)
│   │   ├── cuenta/               Cuentas y CLABE
│   │   ├── transaccion/          Depósitos, retiros, transferencias, tablero
│   │   ├── auditoria/            Bitácora
│   │   ├── config/               Seguridad, OpenAPI, datos iniciales
│   │   └── common/               Manejo global de errores, paginación
│   ├── src/main/resources/db/migration/   Scripts SQL (Flyway)
│   ├── src/main/webapp/WEB-INF/  jboss-web.xml, jboss-deployment-structure.xml
│   └── wildfly/                  Script CLI para el DataSource JNDI
├── frontend/                     Aplicación Angular
└── docker-compose.yml            PostgreSQL
```

## Requisitos

- JDK 17 (o superior)
- Maven 3.9+
- Node.js 20+ y npm
- Eclipse IDE for Enterprise Java and Web Developers
- (Opcional) Docker para PostgreSQL, WildFly 31+ o JBoss EAP 8

## 1. Abrir el backend en Eclipse

1. `File → Import → Maven → Existing Maven Projects`.
2. Root directory: `veripay/backend` → `Finish`. Eclipse descarga las dependencias.
3. Verifica que el proyecto use JDK 17: clic derecho → `Properties → Java Build Path → Libraries`.
4. Ejecutar: clic derecho sobre `VeriPayApplication.java` → `Run As → Java Application`.
5. Pruebas: clic derecho sobre el proyecto → `Run As → JUnit Test` (o `Maven test`).

La aplicación arranca en el perfil `dev` con H2 y datos demo:

- API: http://localhost:8080/veripay/api
- Swagger UI: http://localhost:8080/veripay/swagger-ui.html
- Consola H2: http://localhost:8080/veripay/h2-console (JDBC URL `jdbc:h2:file:./data/veripay`, usuario `sa`)

### Usuarios demo (perfil dev)

| Usuario | Contraseña | Rol |
|---|---|---|
| admin | Admin123! | ADMIN |
| analista | Analista123! | ANALISTA |
| auditor | Auditor123! | AUDITOR |

## 2. Frontend Angular

```bash
cd frontend
npm install
npm start
```

Abre http://localhost:4200. El servidor de desarrollo redirige `/api` al backend (ver `proxy.conf.json`).

En Eclipse puedes editar el frontend instalando **Wild Web Developer** (Eclipse Marketplace) para soporte de TypeScript/Angular, o abrir la carpeta `frontend` con `File → Open Projects from File System`.

## 3. Empaquetar todo en un solo WAR

```bash
cd backend
mvn -Pfull clean package
```

El perfil `full` ejecuta `npm install` + `npm run build` y copia el frontend dentro del WAR. Resultado: `backend/target/veripay.war`.

Ejecutarlo standalone:

```bash
java -jar target/veripay.war
```

y abrir http://localhost:8080/veripay/

## 4. Desplegar en WildFly / JBoss EAP

**Opción rápida (H2 embebido):**

1. Descarga WildFly 31+ (Jakarta EE 10) y descomprímelo.
2. Inicia el servidor: `bin/standalone.bat`.
3. Copia `backend/target/veripay.war` a `standalone/deployments/`.
4. Abre http://localhost:8080/veripay/

**Con PostgreSQL y DataSource administrado por el servidor (JNDI):**

1. `docker compose up -d` (en la raíz del proyecto).
2. Despliega el driver JDBC: `bin/jboss-cli.bat --connect --command="deploy postgresql-42.7.4.jar"`.
3. Crea el DataSource: `bin/jboss-cli.bat --connect --file=backend/wildfly/configurar-datasource.cli`.
4. Arranca WildFly con el perfil `jboss`: `bin/standalone.bat -Dspring.profiles.active=jboss`.
5. Despliega el WAR.

**Desde Eclipse:** `Window → Show View → Servers` → *New Server* → **Red Hat JBoss Middleware → WildFly** (instala *JBoss Tools* desde el Marketplace si no aparece) → agrega el proyecto `veripay` → *Start*.

`jboss-deployment-structure.xml` excluye los subsistemas de logging, JPA, JAX-RS y CDI del servidor, porque Spring Boot trae sus propias implementaciones.

## 5. PostgreSQL con Tomcat embebido

```bash
docker compose up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Flyway crea el esquema automáticamente (`V1__esquema_inicial.sql`).

## Endpoints principales

| Método | Ruta | Descripción | Roles |
|---|---|---|---|
| POST | `/api/auth/login` | Obtiene JWT | público |
| GET | `/api/clientes?texto=&estado=` | Búsqueda paginada | todos |
| POST | `/api/clientes` | Alta de cliente | ADMIN, ANALISTA |
| POST | `/api/clientes/{id}/verificaciones` | KYC biométrico (multipart: `identificacion`, `selfie`) | ADMIN, ANALISTA |
| POST | `/api/cuentas/cliente/{id}` | Abre cuenta con CLABE | ADMIN, ANALISTA |
| POST | `/api/cuentas/{id}/bloqueo` | Bloquea cuenta | ADMIN |
| POST | `/api/transacciones/transferencias` | Transferencia (`Idempotency-Key` opcional) | ADMIN, ANALISTA |
| GET | `/api/transacciones?cuentaId=` | Movimientos | todos |
| GET | `/api/tablero` | Indicadores | todos |
| GET | `/api/auditoria` | Bitácora | ADMIN, AUDITOR |

Ejemplo con curl:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/veripay/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/veripay/api/tablero
```

## Decisiones técnicas

- **Concurrencia en saldos**: cada operación bloquea las cuentas con `SELECT ... FOR UPDATE` en orden ascendente de id, así dos transferencias cruzadas (A→B y B→A) no se interbloquean. La prueba `transferenciasCruzadasConcurrentesConservanElTotal` lanza 8 hilos simultáneos y verifica que el dinero total se conserva. Esa prueba detectó un error real (una lectura previa de la entidad hacía que Hibernate usara una copia desactualizada) que quedó documentado en `CuentaRepository.findIdByClabe`.
- **Idempotencia**: un cliente móvil que reintenta por timeout envía la misma `Idempotency-Key` y recibe la transacción original en lugar de un segundo cargo.
- **Dinero con `BigDecimal`** y `NUMERIC(19,2)`, con `CHECK (saldo >= 0)` en la base de datos como última defensa.
- **Biometría intercambiable**: `ComparadorBiometrico` es una interfaz. La implementación incluida usa hashes perceptuales (aHash + dHash) y **no es reconocimiento facial**; sirve para demostrar el flujo completo. En producción se conecta un proveedor con prueba de vida sin tocar el resto del código.
- **Un solo artefacto**: el mismo WAR corre en Tomcat embebido o en JBoss/WildFly (`SpringBootServletInitializer` + Tomcat en scope `provided`).
- **Esquema versionado** con Flyway y `ddl-auto=validate`: Hibernate nunca modifica la base, solo verifica que el mapeo coincida con el SQL.
