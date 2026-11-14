# Notas del taller — Lección 3: DDD en rica-api

## Paso 1 — Lenguaje Ubicuo: auditoría rápida

1. ¿Hay algún campo o método con un nombre genérico (data, info, value, item) que debería tener un nombre del dominio?

Respuesta: No, en estos archivos no se evidencian nombres de atributos ambiguos. nombreCompleto, correoInstitucional, grupoInvestigacion, id, nombreCompleto, grupoInvestigacion. Estos atributos corresponden al dominio investigadores y no es necesario traducción. A excepción de id, pero esto es un estándar para la entidad.

2. ¿correoInstitucional y grupoInvestigacion son términos que reconocería alguien de la Facultad sin que se los tradujeran? (Spoiler: sí — por eso no los vas a renombrar. El Lenguaje Ubicuo no siempre implica cambiar nombres; a veces implica confirmar que ya están bien y dejar constancia de por qué.)

?

3. En Publicacion, el campo se llama investigadorCorreo, no investigadorId ni autorId. Escribe una frase explicando por qué ese nombre es más preciso para el Lenguaje Ubicuo de este dominio que una alternativa genérica como refId.

Respuesta: Porque es lenguaje que permite reconocer que la referencia apunta directamente a un investigador y que la forma de referenciarlo es su correo institucional

Un campo como refId generaría conflictos si mañana se requiere dentro de publicación referencias otra entidad, nadie sabría refId a qué referencia sin consultar la documentación.

## Paso 4 — Límite del Agregado: Investigador

- **Raíz del Agregado**: `Investigador` — es la única clase del paquete `investigadores` con `@Entity`.
- **Qué vive dentro del límite**: `id`, `nombreCompleto`, `correoInstitucional` (ahora `CorreoInstitucional`, un Value Object) y `grupoInvestigacion`.
- **Por qué `Publicacion` no está dentro**: un investigador puede tener muchas publicaciones. Meterlas dentro del Agregado violaría la regla de Agregados pequeños de Vernon, y cada cambio a un investigador tendría que cargar y transaccionar también todas sus publicaciones.
- **Qué pasaría con un `List<Publicacion> publicaciones` directo en `Investigador`**: rompería el límite — dos transacciones sobre el mismo investigador podrían pisarse esa lista entre sí, el Agregado dejaría de ser pequeño, y `publicaciones` no podría evolucionar ni desplegarse de forma independiente de `investigadores`.

## Lección 4 — Hexagonal en rica-api

### Paso 1 — Auditoría de puertos y adaptadores existentes

1. **`InvestigadorRepository` — ¿puerto primario o secundario?**

   Secundario. Quien inicia la llamada es siempre el núcleo (`InvestigadorService`, `InvestigadorFactory`) hacia afuera, para pedirle algo a la base de datos — nunca al revés. Un puerto primario es al contrario: algo externo (HTTP, un test) llama hacia el núcleo.

2. **`InvestigadorController` — ¿adaptador primario o secundario? ¿qué tecnología envuelve?**

   Primario. Envuelve Spring MVC / HTTP: traduce una petición externa (`GET`/`POST` sobre `/api/investigadores`) en una llamada al núcleo, y la respuesta del núcleo de vuelta en JSON.

3. **`InvestigadorService` es una clase concreta, no una interfaz — ¿qué falta?**

   Falta un puerto primario explícito: una interfaz (`InvestigadorUseCase`) que declare el contrato de casos de uso (`listarTodos`, `buscarPorId`, `registrar`), que `InvestigadorService` implemente y de la que dependa `InvestigadorController` — en vez de depender directamente de la clase concreta.

4. **`InvestigadorFactory` — ¿núcleo o adaptador?**

   Núcleo. Sus imports son solo `Investigador`, `CorreoInstitucional`, `CorreoDuplicadoException` e `InvestigadorRegistrado` (todo del propio dominio/paquete), más `InvestigadorRepository` y, de Spring, `@Component` y `ApplicationEventPublisher` — una anotación y una abstracción del framework, no un detalle concreto de una tecnología externa (JPA, HTTP). No importa nada de `jakarta.persistence` ni de Spring MVC.

### Paso 5 — nota sobre el Fake

`InvestigadorFactory` en este proyecto ya tenía un segundo colaborador desde antes del taller (`ApplicationEventPublisher`, para publicar `InvestigadorRegistrado`), que la guía no menciona porque asume la Factory del Taller 3 tal cual. Para no usar Mockito, `InvestigadorServiceConFalsoTest` le pasa una lambda `evento -> { }` en vez de un mock — `ApplicationEventPublisher` es, en la práctica, una interfaz funcional (un solo método abstracto, `publishEvent(Object)`), así que sigue siendo "un objeto escrito a mano", no un framework de mocking.

### Paso 6 — Confirmación en Git

Commit sobre `taller-hexagonal` (creada desde `taller-ddd`) con los puertos y adaptadores explícitos de `investigadores`: `InvestigadorUseCase` (puerto primario), `RepositorioInvestigadores` + `InvestigadorRepositoryJpaAdapter` (puerto secundario mínimo + adaptador), reorganización en `dominio/aplicacion/infraestructura.entrada.web/infraestructura.salida.persistencia`, y `RepositorioInvestigadoresFalso` + `InvestigadorServiceConFalsoTest` probando el núcleo sin Spring ni Mockito.

## Lección 5 — Descomposición real: rica-api en dos servicios

### Paso 1 — El corte

| Paquete / clase actual | Destino |
|---|---|
| `investigadores.*` (dominio, aplicación, infraestructura) | `investigadores-service` |
| `publicaciones.*` | `publicaciones-service` |
| `compartido.GlobalExceptionHandler`, `RecursoNoEncontradoException` | Se duplica en ambos — cada servicio necesita su propio manejo de errores; ya no hay un único `@ControllerAdvice` compartido |
| `plataforma.StatusController`, `CorsConfig` | Se duplica en ambos, adaptado (cada servicio expone su propio `/api/status`) |
| `plataforma.ArranqueInformativo`, `SaludoInstitucionalService` | Se descarta — demostración del Tutorial 2 (IoC/DI) sin valor de negocio |

**Decisión de arquitectura que fuerza el corte:** duplicar `compartido` y `plataforma` es la primera consecuencia real de *Database per Service* y despliegue independiente. Evitarlo exigiría un *shared kernel* (librería/módulo común versionado por separado), fuera del alcance de este taller.

### Ajustes hechos al copiar el código

- `GlobalExceptionHandler` de cada servicio solo maneja las excepciones de su propio dominio (`CorreoDuplicadoException` en investigadores; `LimiteAnualExcedidoException` en publicaciones). El original importaba ambas y no compilaba al separar.
- `LimitePublicacionesAnualesService.puedeRegistrar` recibía un `Investigador` (clase del otro dominio) solo para leer su correo. Ahora recibe el `String` del correo: publicaciones deja de conocer `Investigador` — el "no cruza dominios" de la guía no era cierto en el código real.
- `PublicacionService` depende de `VerificadorInvestigador` (puerto) en vez de `RepositorioInvestigadores`; `VerificadorInvestigadorPendiente` lo implementa aceptando todo correo, con `TODO Taller Leccion 6`.
- Puertos de las bases en el host: PostgreSQL 5436 y MongoDB 27018 (no 5432/27017), para poder convivir con los contenedores del monolito `rica-api` (5434 y 27017).

### Brecha abierta a propósito

Con `VerificadorInvestigadorPendiente`, `POST /api/publicaciones` en `:8081` acepta un correo inventado. La dependencia entre dominios pasó de ser una llamada oculta a una clase explícita y pendiente; se cierra en el Taller de la Lección 6 (adaptador gRPC + Circuit Breaker).

### Cómo correr

```
cd investigadores-service && docker-compose up -d && .\mvnw.cmd spring-boot:run   # :8080, PostgreSQL
cd publicaciones-service  && docker-compose up -d && .\mvnw.cmd spring-boot:run   # :8081, MongoDB
```

### Evidencia

Capturas en `evidencia/`: `1-investigadores-service.png` (:8080), `2-publicaciones-service.png` (:8081) y `3-pruebas.png` (registro de un investigador, una publicación con correo real y otra con correo inventado que también se acepta — la brecha que cierra la Lección 6).

## Lección 6 — Comunicación real entre servicios

### Qué se construyó

| Necesidad | Patrón | Dónde |
|---|---|---|
| Confirmar que un investigador existe antes de registrar una publicación | gRPC síncrono (puerto 9090) | `InvestigadorGrpcService` (servidor) y `VerificadorInvestigadorGrpc` (cliente) |
| No caer en cascada si investigadores está caído | Circuit Breaker (Resilience4j) | `VerificadorInvestigadorGrpc` → HTTP 503 |
| Ninguna dirección de red fija para HTTP | Service Registry (Eureka, :8761) | `eureka-server` |
| Un único punto de entrada | API Gateway (:8000) | `gateway` |
| Notificar un registro sin acoplar | Domain Event + Transactional Outbox (Kafka) | tabla `outbox_event` + `OutboxPoller` |
| Eliminar un investigador sin transacción distribuida | Saga por coreografía | tópicos `investigadores.en-eliminacion` y `publicaciones.marcadas-huerfanas` |

`VerificadorInvestigadorPendiente` se borró: la brecha del Taller 5 quedó cerrada. Con un correo inventado, `POST /api/publicaciones` ahora responde **404** ("No existe un investigador...") en vez de 201. `PublicacionService` no cambió: solo cambió el adaptador del puerto `VerificadorInvestigador` (la ganancia de la arquitectura hexagonal).

### Decisiones y limitaciones

- **La dirección gRPC sigue fija** (`static://localhost:9090`). Eureka resuelve el descubrimiento para el HTTP balanceado del Gateway (`lb://`); integrarlo con el balanceo de canales gRPC es posible pero queda fuera del alcance del taller.
- **Outbox solo en investigadores.** `publicaciones-service` envía su confirmación de la Saga directamente con `KafkaTemplate`. En producción también debería usar un outbox: si cae entre guardar las publicaciones y enviar la confirmación, la Saga se detiene.
- **Compensación de la Saga no implementada** (reto opcional): si publicaciones nunca confirma, el investigador queda en `EN_ELIMINACION` indefinidamente. Faltaría un temporizador que, tras un plazo, regrese el estado a `ACTIVO`.
- **El `.proto` está copiado** en cada servicio (cambia solo `java_package`): cada servicio genera sus clases y ninguno depende del código del otro. En un equipo real viviría en un repositorio compartido.
- **Puertos de las bases** en el host (distintos de los de la guía para convivir con el monolito): PostgreSQL 5436, MongoDB 27018. Kafka en 9092. Los nombres de contenedor son los que genera Docker Compose (`investigadores-service-rica-db-1`, etc.), no `investigadores-db` como dice el taller.
- Kafka se agregó al `docker-compose.yml` de investigadores-service (contenedor `rica-kafka`).

### Verificación realizada

1. Eureka muestra `GATEWAY`, `INVESTIGADORES-SERVICE` y `PUBLICACIONES-SERVICE` en UP.
2. `POST :8000/api/investigadores` → 201; la fila queda en `outbox_event` y el poller la publica en `investigadores.registrados` (consumida desde Kafka).
3. `POST :8000/api/publicaciones` con correo real → 201; con correo inventado → 404 (gRPC).
4. Circuit Breaker: con investigadores detenido, las primeras llamadas dan 503 `UNAVAILABLE` y las siguientes 503 "circuito abierto" sin ir a la red.
5. Saga: `DELETE :8000/api/investigadores/{id}` → 202; el investigador pasa a `EN_ELIMINACION`, publicaciones marca sus publicaciones como huérfanas (`huerfana: true` en Mongo) y confirma, y el investigador se elimina físicamente.

### Cómo correr

```
cd investigadores-service && docker compose up -d      # PostgreSQL + Kafka
cd publicaciones-service  && docker compose up -d      # MongoDB
# cuatro terminales, en este orden, con .\mvnw.cmd spring-boot:run:
eureka-server (:8761) -> investigadores-service (:8080, gRPC :9090) -> publicaciones-service (:8081) -> gateway (:8000)
```

### Evidencia (Lección 6)

En `evidencia/leccion6/`: `0-cuatro-terminales.png` (eureka-server, investigadores-service, publicaciones-service y gateway corriendo a la vez), las cuatro capturas individuales y `5-pruebas-saga-y-bd.png` (registro vía Gateway, publicación por gRPC con correo real y 404 con correo inventado, `DELETE` → 202 y el `SELECT` que ya no lista al investigador eliminado). En los logs de las capturas 2 y 3 se ve la secuencia de la Saga: `Evento N publicado en investigadores.en-eliminacion` → `Saga: 1 publicaciones de ... marcadas como huérfanas` → `Saga: publicaciones confirmó para ... Eliminando investigador.`

## Lección 8 — CQRS: consultas-service como lado de lectura

### Qué se construyó

- **Escritura:** `publicaciones-service` publica `publicaciones.registradas` (solo después de guardar con éxito) e `investigadores-service` publica `investigadores.eliminados` por el Outbox al cerrar la Saga. Ya existían `investigadores.registrados` e `investigadores.en-eliminacion`.
- **Lectura:** `consultas-service` (:8083, base propia `rica_consultas` en la misma instancia de MongoDB) escucha los cuatro eventos y mantiene una `FichaInvestigador` por investigador, con sus publicaciones embebidas. Solo expone `GET` (`/api/consultas/investigadores`, `/investigadores/{correo}`, `/ranking`); el Gateway tiene la ruta `consultas-route`.
- Pruebas: 7 en consultas-service (3 de la ficha, 4 del proyector, incluido el desorden entre tópicos y el duplicado), 1 nueva por cada servicio de escritura.

### Diferencias con la guía del taller

- En Spring Boot 4 la propiedad de MongoDB es `spring.mongodb.uri`, no `spring.data.mongodb.uri` como dice el taller: con la segunda, consultas se conectaba al 27017 por defecto y las consultas HTTP daban timeout. Además, el primer arranque consumió y perdió eventos (no pudo guardarlos), lo que obligó a cambiar el `group-id` y reproducirlos: es el mecanismo del Paso 8 usado "en la vida real".
- Se usó `spring-boot-starter-webmvc` y springdoc 2.8.5 (los mismos que los otros servicios) en vez de `spring-boot-starter-web` y springdoc 3.1.0.
- `LimitePublicacionesAnualesService.puedeRegistrar` ahora recibe `(correo, anio)`, como espera el test de la guía.
- Mongo del host en 27018 (no 27017) y contenedor `publicaciones-service-rica-mongo-1`.

### Explicación por punto del entregable (qué se vería distinto si lectura y escritura fueran el mismo modelo)

1. **Ficha con dos publicaciones:** haría falta un `JOIN` o dos llamadas (investigadores + publicaciones) entre dos bases distintas; aquí un solo documento ya trae todo.
2. **Ranking:** habría que contar publicaciones de todos los investigadores en cada consulta, sobre la base de escritura; aquí `totalPublicaciones` se guarda ya calculado y se ordena directamente.
3. **Log con `Proyección: ...`:** no existiría ese paso; la consulta leería la misma tabla que escribe el comando, sin proyector ni eventos, y los dos lados escalarían y fallarían juntos.
4. **404 tras la Saga:** la ficha desaparece solo porque el evento `investigadores.eliminados` llegó al lado de lectura; con un modelo único, el borrado sería inmediato y atómico, sin consistencia eventual (ni retraso visible).
5. **Fichas reconstruidas:** con un solo modelo no se podría "destruir y rehacer" la lectura, porque sería la fuente de verdad; aquí se borró la colección y se reprodujeron los eventos con un `group-id` nuevo.

### Trampa real observada en la reconstrucción

Tras reproducir los eventos, `luis.mora` volvió a aparecer como `EN_ELIMINACION` (con 3 publicaciones), y también `mario.gomez` y `diego851`, que ya habían sido eliminados. No es un error de código: `investigadores.en-eliminacion` e `investigadores.eliminados` están en tópicos distintos y Kafka no garantiza orden entre tópicos, así que el "eliminado" pudo procesarse antes que el "en eliminación" y la ficha resucitó (en el caso de Mario y Diego, además, sus eliminaciones ocurrieron antes de que existiera el tópico `investigadores.eliminados`). En producción se evitaría publicando todos los eventos de un agregado en un solo tópico, con número de versión.

### Evidencia (Lección 8)

En `evidencia/leccion8/`: `1-ficha-ranking-y-404.png` (ficha con 2 publicaciones en una sola consulta, ranking ordenado, `DELETE` → 202 y la ficha eliminada devolviendo 404), `3-log-consultas-proyeccion.png` (líneas `Proyección: ...`, incluidos el replay y los eventos en vivo) y `5-lista-reconstruida.png` (fichas reconstruidas tras el Paso 8).

## Lección 9 — Consumidores robustos en consultas-service

### Qué se construyó

- **Reintentos con backoff + Dead Letter Topic** (`ManejoErroresConfig`, `TopicosConfig`): `JacksonException` y `NullPointerException` (JSON roto, campo faltante) no se reintentan y van directo a `investigadores.registrados.DLT`; el resto se reintenta con espera 1 s, 2 s, 4 s antes de ir a la DLT.
- **Consumidor idempotente** (`EstadisticasListener`, `RegistroEventosProcesados`, `EventoProcesado`): segundo consumidor del mismo tópico (grupo `consultas-estadisticas`) que cuenta altas. Registra `topico-particion-offset` como `_id` en `eventos_procesados`; el segundo insert falla con `DuplicateKeyException` y el evento se ignora.
- Endpoint `GET /api/consultas/estadisticas` y 12 tests en consultas-service (7 del Taller 8 + 5 nuevos).

### Diferencia con la guía

Spring Kafka 4 (Spring Boot 4.1) nombra por defecto la DLT con el sufijo `-dlt` (en minúscula, `investigadores.registrados-dlt`), no `.DLT` como dice el taller. Con la configuración tal cual, el mensaje venenoso iba a un tópico auto-creado distinto y `investigadores.registrados.DLT` quedaba vacío. Se fijó el destino a mano en el `DeadLetterPublishingRecoverer` (`registro.topic() + ".DLT"`). El tópico huérfano `investigadores.registrados-dlt` quedó creado en Kafka por la primera prueba y no se usa.

### Resultados observados

| Paso | Observación |
|---|---|
| 2. Línea base (sin defensas) | 9 reintentos inmediatos (~0,5 s entre ellos) + el intento original, luego `Backoff ... exhausted for investigadores.registrados-0@7` y el mensaje se descarta |
| 3. Con DLT | un solo error, sin reintentos; el mensaje queda en la DLT con `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message`, `kafka_dlt-original-topic`, partición, offset y grupo |
| 4. Contador ingenuo | N = 6 (altas históricas), tras registrar uno: N+1 = 7 |
| 5. Duplicados | tras devolver el grupo al principio: **14** (cada alta contada dos veces), sin ningún error en el log |
| 6. Idempotente | mismo escenario: **7** exacto; al repetirlo, el log dice `evento ... ya procesado, se ignora` por cada mensaje |

### Paso 7 — Discusión: ¿qué hacemos con la DLT?

1. **¿Quién debería enterarse y cómo?** El equipo dueño del consumidor (en este caso consultas-service), con una alerta automática cuando el tamaño de la DLT sube por encima de cero (métrica de lag/offset de la DLT que dispare un aviso por correo o chat) y un tablero con los mensajes pendientes; hoy nadie la mira y el mensaje se queda guardado sin que nadie lo sepa.
2. **¿Cómo se reprocesa un mensaje tras corregir el error?** Con una herramienta o tarea explícita que lea la DLT y reenvíe los mensajes ya corregidos al tópico original (o directamente a la lógica de proyección), con el `original-topic` y el `original-offset` de las cabeceras como trazabilidad; reenviar a mano al tópico original sin control puede reintroducir el mensaje a todos los consumidores del tópico, no solo al que falló.
3. **¿Cuándo es válido descartar y quién decide?** Cuando el mensaje es basura que ningún arreglo puede recuperar (por ejemplo, un mensaje de prueba o con un formato que ningún productor legítimo envía), y lo decide el dueño del consumidor junto con el dueño del productor, dejando registro de qué se descartó y por qué.
4. **¿Y si el mensaje de la DLT es válido pero llegó a destiempo?** Hay que comprobar el estado actual antes de aplicarlo: si el hecho que describe ya quedó superado por otros eventos (por ejemplo, el investigador ya fue eliminado), aplicarlo ciegamente resucita datos, como la ficha que reapareció en el Taller 8; por eso el reproceso debe ser idempotente y consultar el estado actual o usar números de versión.

**Conexión con la Lección 10 (observabilidad):** nada de lo anterior es posible sin medir: métricas del tamaño y la antigüedad de la DLT, logs con el identificador del evento (`topico-particion-offset`), trazas que permitan seguir un mensaje de un servicio a otro, y alertas sobre ellas.

### Por qué reintentar sin fin un mensaje venenoso es peor que perderlo y peor que enviarlo a una DLT

Reintentar sin fin un mensaje que no se puede procesar bloquea la partición (nada detrás de él avanza) y consume recursos sin resolver nada, así que afecta a todos los mensajes sanos; es peor que perderlo porque con la pérdida al menos el consumidor sigue, y peor que la DLT porque la DLT también mantiene el flujo y además conserva el mensaje con su diagnóstico para que alguien lo revise o lo reprocese.

### Límites conocidos de la idempotencia implementada

- El id por posición (`tópico-partición-offset`) no detecta duplicados del productor (publicar dos veces el mismo hecho genera dos offsets); haría falta un id de negocio en el evento (por ejemplo, el id del Outbox).
- Registrar el id y sumar son dos escrituras: si el proceso cae entre ambas se pierde un conteo; cerrarlo exige una transacción que cubra ambas o un efecto idempotente por naturaleza (calcular el total con un `count` sobre los eventos registrados).

### Evidencia (Lección 9)

En `evidencia/leccion9/`: `1-linea-base-reintentos.png`, `2-dlt-con-cabeceras.png`, `3-estadisticas-descuadre-y-idempotente.png` (valores antes/después del Paso 5 y tras repetir los eventos con el consumidor idempotente) y `4-log-ya-procesado.png` (líneas `evento ... ya procesado, se ignora`).

Nota sobre la captura 4: las líneas se extrajeron del buffer de la propia consola donde corre `consultas-service` (el log de arranque se había desplazado fuera de la vista) y se reordenaron una por registro de log, sin alterar su contenido. La captura 1 se construyó igual a partir del log del servicio guardado en archivo.

## Lección 10 — Observabilidad real en RICA

### Qué se construyó

- **Spring Boot Actuator** en investigadores-, publicaciones- y consultas-service (`/actuator/health`, `/info`, `/metrics`) y el patrón de log `%5p [${spring.application.name},%X{correlationId:-}]`: cada línea muestra servicio e id de correlación.
- **`CorrelationIdFilter`** (investigadores-service): toma el id de la cabecera `X-Correlation-Id` o genera un UUID, lo pone en el MDC, lo devuelve en la respuesta y lo limpia en un `finally`.
- **El id viaja dentro de los eventos:** `OutboxPublicadorEventos` lo copia desde el MDC al JSON del evento; `InvestigadorEnEliminacionListener` (publicaciones) y `PublicacionesMarcadasListener` (investigadores) lo leen del mensaje, lo ponen en el MDC con `MDC.putCloseable` y lo reenvían en la confirmación.
- Línea de log nueva en `InvestigadorService.iniciarEliminacion` (`Saga: inicia la eliminación de ...`): sin ella, el primer paso de la Saga no deja ninguna línea con el id.

### Diferencias con la guía

- Agregué también `spring-boot-starter-micrometer-metrics` desde el principio (la guía lo sugiere solo si `/actuator/metrics` da 404).
- El grupo de consultas actual es `consultas-service-v3` (la guía menciona `v2`).
- En el Paso 8 la guía dice "detén publicaciones-service, crea un investigador con una publicación y elimínalo": con publicaciones detenido no se puede registrar la publicación, así que primero se creó el investigador con su publicación y después se detuvo el servicio.
- No se ejecutó una "línea base" en vivo (Paso 2) antes de aplicar los cambios: no hay evidencia asociada en el entregable.

### Resultados observados

- `/actuator/health` UP en los tres servicios (investigadores con `db` PostgreSQL; publicaciones y consultas con `mongo`).
- Con `X-Correlation-Id: demo-001`, el id se devolvió en la respuesta y la Saga se reconstruyó buscándolo en los logs:
  - investigadores-service: `Saga: inicia la eliminación de ...` y `Saga: publicaciones confirmó para ... Eliminando investigador.`
  - publicaciones-service: `Saga: 2 publicaciones de ... marcadas como huérfanas`
  - consultas-service: **ninguna línea** con el id (brecha).
- `/actuator/metrics/http.server.requests`: COUNT, TOTAL_TIME y MAX (señales R y D del método RED); con `?tag=status:404`, el error provocado (E).
- Lag de `consultas-service-v3`: 0 antes; **3** en `investigadores.registrados` con consultas detenido y tres altas nuevas; 0 al volver a arrancarlo.
- Saga atascada (`demo-002`): con publicaciones detenido, la ficha del investigador quedó en `EN_ELIMINACION` y el grupo `publicaciones-service` mostró lag 1 en `investigadores.en-eliminacion`. Al reiniciarlo, procesó el evento pendiente, la Saga continuó con el mismo id y la ficha desapareció.

### Brecha de trazabilidad en consultas-service

`ProyeccionListener` no lee `correlationId` del mensaje, así que el proyector escribe sus líneas con el id vacío (`[consultas-service,]`) y la traza de la Saga se corta en el lado de lectura. Cerrarla exige el mismo patrón del Paso 5 en cada método del listener: leer `correlationId` del JSON (o `"sin-correlacion"`), ponerlo en el MDC con `MDC.putCloseable` mientras se llama al proyector, y dejar que el `try-with-resources` lo restaure. No se aplicó porque el taller lo pide como nota, no como cambio.

### Paso 9 — Discusión: ¿y si nunca vuelve?

1. **Falla permanente de publicaciones:** el investigador quedaría en `EN_ELIMINACION` indefinidamente (bloqueo semántico: nunca desaparece a medias, pero tampoco se recupera) y el mensaje se iría a la DLT o reintentaría sin fin según la configuración del consumidor. Hoy nadie se daría cuenta salvo que alguien mire la ficha en consultas o el lag del grupo.
2. **Compensación:** un temporizador (o un job) en investigadores-service detecta investigadores en `EN_ELIMINACION` por más de un plazo y publica `investigadores.eliminacion-cancelada` (por el Outbox, con el mismo `correlationId`); investigadores lo consume para devolver el estado a `ACTIVO`, publicaciones lo consume para desmarcar las huérfanas de ese correo, y consultas para volver la ficha a `ACTIVO`. Sería idempotente si cada paso solo fija un estado destino (`ACTIVO`/`huerfana=false`) en vez de alternarlo, y si ignora el evento cuando el investigador ya está `ACTIVO` o ya fue eliminado.
3. **Métrica y alerta:** una gauge `sagas_eliminacion_atascadas` (investigadores en `EN_ELIMINACION` hace más de 10 minutos, consultando la tabla) con alerta si es mayor que cero durante 5 minutos, más el lag del grupo `publicaciones-service` en `investigadores.en-eliminacion` con alerta si supera 0 por más de unos minutos.
4. **Coreografía u orquestación con cuatro pasos más:** orquestación. Con seis pasos, cada servicio que reacciona al evento del anterior vuelve el flujo difícil de seguir, de depurar y de compensar (hay que saber qué paso falló y deshacer en orden); un orquestador concentra el estado de la Saga, los plazos y las compensaciones en un solo lugar, a costa de un componente más y de acoplar los participantes a él.

### Evidencia (Lección 10)

En `evidencia/leccion10/`: `1-actuator-health.png`, `2-saga-demo-001.png` (líneas con el id en los tres servicios y la brecha de consultas), `3a-metricas-http.png`, `3b-lag-antes-durante-despues.png` y `4-saga-atascada-en-eliminacion.png`.

## Lección 11 — Contenedores y CI real para RICA

### Qué se construyó

- `eureka-server/Dockerfile` multi-etapa (Maven + JDK 25 para compilar; JRE 25 sin privilegios para ejecutar), `.dockerignore` y `docker-compose.yml`.
- Corrección del hostname de Eureka (`eureka.instance.hostname: localhost`), tal como indica el taller para que dentro del contenedor no intente replicarse contra sí mismo.
- `.github/workflows/ci.yml` en la raíz del repositorio: matriz de 5 jobs (eureka-server, gateway, investigadores-, publicaciones- y consultas-service) que ejecutan las pruebas unitarias. Se adaptó `working-directory` a `rica-microservicios/<servicio>` porque en este repositorio los servicios viven en esa subcarpeta, y `cache-dependency-path` apunta al `pom.xml` de cada servicio.

### Resultados observados

- Imagen `rica/eureka-server:0.1`: **590 MB en disco (171 MB de contenido)**; la capa del `.jar` pesa 60,7 MB y el resto es el JRE 25 de Temurin.
- Contenedor con `docker run -p 8761:8761`: el panel de Eureka respondió desde el contenedor (captura `2-eureka-panel-contenedor.png`).
- Caché de capas: el segundo `docker build`, sin cambios, tardó 3 s y marcó como `CACHED` todas las capas. Tras editar `application.yml` (6 s), solo se volvieron a ejecutar `COPY src`, `mvn package` y el `COPY --from=build` del jar; `COPY pom.xml` y `mvn dependency:go-offline` siguieron en caché. (En la captura, los pasos `FROM` aparecen como "ejecutado" porque solo se resuelve la imagen base; no tienen marca `CACHED`.) El primer build tardó más de 10 minutos por la red (descarga de Maven, Temurin 25 y dependencias).
- Compose: `up -d --build` reutilizó todas las capas, el servicio quedó `Up` y `down` lo eliminó junto con su red.
- Variables de entorno: la misma imagen con `-e SERVER_PORT=9000` respondió en el puerto 9000 con "No instances available" (los servicios siguen apuntando al 8761).
- Redis: `SET ... EX 30`, `GET` (hit), `TTL`, `GET` tras 32 s (`(nil)`, miss) y `SET` + `DEL` + `GET` (invalidación). PowerShell eliminó las comillas del JSON de ejemplo al pasarlo a `redis-cli`; no afecta al patrón.
- No se reprodujo a propósito el error de replicación de Eureka (`ReplicationTaskProcessor ... SocketTimeoutException`) antes de aplicar la corrección: el hostname se fijó desde el principio.
### Respuestas del Paso 2 (Dockerfile multi-etapa)

- **(a) ¿Por qué se copia primero `pom.xml` y luego `src`?** Porque Docker cachea cada instrucción como una capa y la invalida solo si cambia lo que esa instrucción copia o ejecuta. El `pom.xml` cambia poco y el código cambia casi siempre: si se copiara todo junto, cada cambio de una línea de código invalidaría la capa de descarga de dependencias y habría que bajar todo de nuevo. Con este orden, `dependency:go-offline` queda en caché mientras el `pom.xml` no cambie y solo se recompila el código.
- **(b) ¿Qué contiene la imagen final y qué no?** Contiene el JRE 25 de Temurin, un usuario sin privilegios (`rica`) y el `app.jar` ya compilado. No contiene Maven, el JDK completo, el código fuente, el repositorio `~/.m2` ni los archivos de `target/` (la primera etapa se descarta). Por eso pesa mucho menos y expone menos superficie de ataque.
- **(c) ¿Por qué `USER rica`?** Para no ejecutar el proceso como `root` dentro del contenedor: si alguien explotara una vulnerabilidad de la aplicación, tendría los permisos de un usuario de sistema sin privilegios y no los de `root`, lo que limita el daño (principio de mínimo privilegio).

### Respuesta del Paso 5 (variables de entorno)

Con una sola imagen y variables de entorno (`SERVER_PORT=9000`) el artefacto que se prueba es exactamente el que se despliega: lo único que cambia entre desarrollo, pruebas y producción es la configuración inyectada desde fuera (puerto, URLs, credenciales). Si hubiera una imagen distinta por entorno, lo que se probó ya no sería lo que se despliega, habría que construir y guardar N imágenes, y se mezclarían configuración y secretos con el código.

### Tabla de decisión de almacenes (Paso 6.6) — módulo `consultas-service` de RICA

| Tipo de dato | ¿Qué almacén? | ¿Por qué? / consulta principal que lo justifica | Costo de añadirlo |
|---|---|---|---|
| Investigadores (comando: registro, estado) | PostgreSQL (relacional) | Integridad y transacciones: correo único, estado `ACTIVO`/`EN_ELIMINACION`, una Saga de eliminación que lee y cambia una fila. Consulta: `existeCorreo`, `buscarPorCorreo` | Ya está en el proyecto; migraciones con Flyway |
| Publicaciones (comando) | MongoDB (documental) | Esquema flexible por tipo (`detalles` distinto para artículo o libro) y escritura simple. Consulta: `findByInvestigadorCorreo` y conteo por año | Ya está en el proyecto |
| Ficha de lectura (investigador + publicaciones) | MongoDB (modelo de lectura desnormalizado) | Un solo documento responde la consulta completa sin joins: `GET /api/consultas/investigadores/{correo}` y el ranking por `totalPublicaciones` | Ya está en el proyecto; hay que mantener la proyección por eventos |
| Eventos entre servicios | Kafka (log de eventos) | Reproducir, desacoplar productor y consumidor y reconstruir el modelo de lectura. Consulta: leer desde un offset | Ya está en el proyecto; costo operativo alto (broker, lag, DLT) |
| Caché de fichas calientes | Redis (clave-valor con TTL) | Aliviar lecturas repetidas de la misma ficha: `GET ficha:{correo}` con TTL e invalidación al llegar un evento | Un servicio más que operar, invalidar bien (si no, datos viejos) y decidir el TTL; **hoy no se justifica** |
| Búsqueda de texto de títulos y resúmenes | Motor de búsqueda (Elasticsearch/OpenSearch) o un índice de texto de MongoDB | Consultas "contiene la palabra X" con relevancia que un `LIKE` no resuelve bien | Otro clúster que sincronizar; mejor empezar con el índice de texto de MongoDB |
| Contadores y estadísticas | MongoDB (`$inc` atómico) o una vista calculada | Contar altas sin recorrer todo; la idempotencia se resuelve con la tabla de eventos procesados | Sin costo extra (misma base) |

**Párrafo final:** en RICA ya se usan tres tecnologías (PostgreSQL, MongoDB y Kafka) y cada una resuelve un problema distinto y real: transacciones y unicidad para los investigadores, documentos flexibles para las publicaciones y un log de eventos para desacoplar servicios y reconstruir la lectura. Más allá de eso, Redis y un motor de búsqueda serían optimizaciones sin una consulta lenta que las justifique todavía: el modelo de lectura ya es un solo documento por investigador y la carga actual es mínima. Una base relacional bien usada bastaría para casi todo el proyecto, y cada tecnología adicional suma servicio que operar, datos que mantener sincronizados y fallas posibles; añadiría una caché solo después de medir una latencia que lo justifique (por ejemplo, con las métricas del Taller 10).

### Lectura opcional — qué haría falta para ejecutar `consultas-service` también como contenedor

1. Reutilizar el mismo `Dockerfile` multi-etapa cambiando el puerto expuesto a 8083.
2. Ponerlo en la misma red de Docker que MongoDB, Kafka y Eureka (`docker network` o un `docker-compose.yml` raíz con todos los servicios), porque `localhost` dentro del contenedor ya no es el host.
3. Cambiar las direcciones por variables de entorno que apunten a los nombres de servicio de esa red: `SPRING_MONGODB_URI=mongodb://rica-mongo:27017/rica_consultas`, `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` y `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka`.
4. Reconfigurar Kafka con dos listeners (uno interno para los contenedores, `kafka:9092`, y otro externo para el host) porque hoy anuncia `localhost:9092`, que un contenedor no alcanza.
5. Ordenar el arranque (`depends_on` con healthchecks) y revisar que Eureka registre la IP o el nombre alcanzable del contenedor (`prefer-ip-address` ya está activo).
