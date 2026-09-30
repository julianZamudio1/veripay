# Arquitectura

## Vista general

```mermaid
flowchart LR
    U[Navegador] -->|HTTPS| A[Angular 20<br/>SPA]
    A -->|JSON + JWT<br/>/api/v1| S[Spring Boot 3.3<br/>WAR]
    S -->|JPA / Flyway| D[(H2 · PostgreSQL)]
    S -.->|interfaz<br/>ComparadorBiometrico| B[Comparador de imágenes]
    subgraph Servidor de aplicaciones
      S
    end
```

El backend se empaqueta como un solo WAR que incluye el frontend compilado. El mismo archivo corre de dos formas:

- `java -jar veripay.war`, con Tomcat embebido.
- Copiado a `standalone/deployments` de WildFly o JBoss EAP. `jboss-web.xml` fija el contexto en `/veripay`.

## Módulos del backend

Cada paquete agrupa un tema del negocio con su entidad, repositorio, servicio y controlador.

| Paquete | Responsabilidad |
|---|---|
| `auth` | Login y emisión del JWT |
| `usuario` | Usuarios del sistema y roles; `UsuarioDetailsService` para Spring Security |
| `cliente` | Alta y búsqueda de clientes, validación de CURP (`Curp`, `@CurpValida`) |
| `biometria` | Verificación KYC: `KycService` aplica las reglas y `ComparadorBiometrico` compara imágenes |
| `cuenta` | Cuentas, generación y validación de CLABE (`Clabe`) |
| `transaccion` | Depósitos, retiros, transferencias, idempotencia y tablero |
| `auditoria` | Bitácora de eventos |
| `config` | Seguridad, propiedades, OpenAPI, datos iniciales, reenvío de rutas del SPA |
| `common` | Errores RFC 9457 (`GlobalExceptionHandler`, `Problemas`), excepciones de negocio y paginación |

### Capas

```mermaid
flowchart TB
    C[Controller<br/>HTTP, validación de entrada, roles, cabeceras] --> SV[Service<br/>reglas de negocio, transacciones, auditoría]
    SV --> R[Repository<br/>Spring Data JPA, bloqueos]
    R --> DB[(Base de datos)]
    SV --> E[Entidades<br/>invariantes: saldo, estado]
```

- Los **controladores** no contienen reglas de negocio. Validan el formato con Bean Validation, revisan el rol con `@PreAuthorize` y arman la respuesta HTTP (`201`, `Location`, `Idempotent-Replayed`).
- Los **servicios** abren la transacción (`@Transactional`), aplican las reglas y registran el evento de auditoría dentro de la misma transacción: si la operación se revierte, el evento también.
- Las **entidades** protegen sus invariantes. `Cuenta.cargar` rechaza un saldo negativo y una cuenta bloqueada; el servicio no puede saltarse esa regla.
- La API expone **DTOs** (`record`), nunca entidades JPA.

## Flujo de una transferencia

```mermaid
sequenceDiagram
    participant F as Angular
    participant C as TransaccionController
    participant S as TransaccionService
    participant R as CuentaRepository
    participant DB as Base de datos

    F->>C: POST /transacciones/transferencias<br/>Idempotency-Key: k
    C->>S: transferir(req, k, usuario)
    S->>DB: ¿existe la clave k?
    alt clave ya usada con los mismos datos
        S-->>C: transacción original, repetida = true
        C-->>F: 201 + Idempotent-Replayed: true
    else clave nueva
        S->>R: findIdByClabe(origen), findIdByClabe(destino)
        S->>R: SELECT ... FOR UPDATE (id menor primero)
        S->>R: SELECT ... FOR UPDATE (id mayor)
        S->>S: validar KYC, cargar origen, abonar destino
        S->>DB: INSERT transaccion + evento_auditoria
        S-->>C: transacción nueva
        C-->>F: 201 + Location
    end
```

Bloquear siempre en orden de id evita el interbloqueo entre A→B y B→A simultáneas. `findIdByClabe` devuelve solo el id: si cargara la entidad antes del `FOR UPDATE`, Hibernate reutilizaría esa copia sin refrescar y el saldo quedaría desactualizado. La prueba de concurrencia con 8 hilos encontró ese error.

## Modelo de datos

```mermaid
erDiagram
    USUARIO {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar rol
        boolean activo
    }
    CLIENTE ||--o{ VERIFICACION_BIOMETRICA : "tiene"
    CLIENTE ||--o{ CUENTA : "es titular de"
    CUENTA ||--o{ TRANSACCION : "origen"
    CUENTA ||--o{ TRANSACCION : "destino"
    CLIENTE {
        bigint id PK
        varchar curp UK
        varchar rfc
        varchar email UK
        date fecha_nacimiento
        varchar estado_kyc
        bigint version
    }
    VERIFICACION_BIOMETRICA {
        bigint id PK
        bigint cliente_id FK
        numeric puntaje
        numeric umbral
        boolean aprobada
        varchar huella_identificacion
        varchar huella_selfie
    }
    CUENTA {
        bigint id PK
        varchar clabe UK
        bigint cliente_id FK
        numeric saldo "CHECK >= 0"
        varchar estado
        bigint version
    }
    TRANSACCION {
        bigint id PK
        varchar folio UK
        varchar tipo
        bigint cuenta_origen_id FK
        bigint cuenta_destino_id FK
        numeric monto "CHECK > 0"
        varchar clave_idempotencia UK
    }
    EVENTO_AUDITORIA {
        bigint id PK
        varchar usuario
        varchar accion
        varchar entidad
        varchar entidad_id
        timestamp creado_en
    }
```

- Flyway crea el esquema desde `db/migration/V1__esquema_inicial.sql`. Hibernate corre con `ddl-auto=validate`: revisa que el mapeo coincida con las tablas y nunca las modifica.
- El SQL funciona igual en H2 (modo PostgreSQL) y en PostgreSQL 14 o superior.
- Los montos usan `NUMERIC(19,2)` en la base y `BigDecimal` en Java. Las restricciones `CHECK` son la última defensa si un error de código intentara un saldo negativo.
- `transaccion.clave_idempotencia` es única: dos peticiones simultáneas con la misma clave no pueden insertar dos veces.

## Frontend

| Carpeta | Contenido |
|---|---|
| `core/` | `AuthService` (sesión con signals), `authInterceptor` (agrega el JWT, cierra sesión ante un 401), `authGuard`, `ApiService` (una función por endpoint), modelos y validador de CURP |
| `layout/` | `Shell`: menú lateral filtrado por rol y barra superior |
| `pages/` | Una carpeta por pantalla, cargada bajo demanda (*lazy loading*) |

- Componentes standalone con control de flujo nuevo (`@if`, `@for`) y signals para el estado.
- Formularios reactivos con las mismas reglas que el backend. `validadores.ts` implementa el dígito verificador de la CURP con el mismo algoritmo que `Curp.java`.
- Las pantallas de depósito y transferencia generan un `Idempotency-Key` por operación y lo conservan si la petición falla, así un reintento no duplica el cargo. Si el usuario cambia algún dato, se genera una clave nueva.
- En desarrollo, `ng serve` redirige `/api` al backend con `proxy.conf.json`. En producción, el frontend se compila con `baseHref: /veripay/` y viaja dentro del WAR.

## Decisiones de diseño

| Decisión | Motivo |
|---|---|
| Un WAR para Tomcat y WildFly | La vacante pide JBoss. `SpringBootServletInitializer` y Tomcat en scope `provided` permiten desplegar el mismo artefacto en ambos. |
| Bloqueo pesimista en saldos | Con dinero, un reintento por conflicto optimista complica al cliente. El `FOR UPDATE` serializa solo las cuentas involucradas. |
| `ComparadorBiometrico` como interfaz | La comparación incluida es de demostración. Un proveedor real se conecta con una clase nueva sin tocar `KycService`. |
| Errores RFC 9457 | Es el estándar del IETF para errores HTTP y Spring 6 lo soporta de forma nativa. |
| Versión en la ruta | Es visible, fácil de enrutar en un proxy y de probar con `curl`. |
