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
