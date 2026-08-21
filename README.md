# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

Fabian Andrade / Juan Diego Melo

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

---

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`el reporte de laboratorio`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Reporte de laboratorio

### 1. Data races encontradas y su solución

| Componente | Problema encontrado | Consecuencia | Solución aplicada |
| :--- | :--- | :--- | :--- |
| `Snake.java` | `body` es un `ArrayDeque<Position>` leído por el hilo de Swing (`snapshot()`) mientras el hilo virtual de la serpiente lo modifica (`advance()`: `addFirst`/`removeLast`). | `ConcurrentModificationException` o cuerpo de la serpiente corrupto/parpadeando en pantalla. | Se sincronizan (`synchronized`) todos los métodos que leen o mutan `body` (`advance()`, `snapshot()`, `head()`, `size()`). `snapshot()` retorna una copia atómica (`new ArrayDeque<>(body)`) tomada dentro del bloque sincronizado, para que la UI nunca vea un estado a medias. `direction` y `alive` se marcan `volatile` para visibilidad inmediata entre hilos. |
| `GameClock.java` | `pause()` solo cambiaba el estado usado para el repintado; no existía ningún mecanismo que avisara a los hilos de las serpientes. | Pausa falsa: las serpientes seguían moviéndose a máxima velocidad en segundo plano mientras la UI aparentaba estar pausada. | Se agrega el método `checkPaused()`, invocado por cada `SnakeRunner` antes de cada paso, que bloquea el hilo en un monitor (`pauseLock.wait()`) mientras el estado sea `PAUSED`. |
| `SnakeApp.java` | Lectura de la serpiente más larga y de la peor serpiente en dos pasos separados, sin garantía de que el estado del tablero no cambiara entre una lectura y otra. | *Tearing*: la UI podía mostrar una combinación inconsistente de estados (una estadística "vieja" con otra "nueva"). | Ambas estadísticas se calculan **después** de pausar el `GameClock` (los hilos ya están detenidos) y dentro del mismo bloque de repintado en el EDT de Swing (`SwingUtilities.invokeLater`), garantizando una fotografía consistente del estado. |

### 2. Colecciones mal usadas y cómo se protegieron o sustituyeron

- **`Snake.body` (`ArrayDeque<Position>`)**: no es *thread-safe*. En vez de sustituirla (lo que habría penalizado el rendimiento de `addFirst`/`removeLast`), se protegió con `synchronized` en todos sus puntos de acceso, ya que la región crítica es pequeña y de corta duración.
- **Registro de serpientes muertas en `Board.java`**: se necesitaba una estructura FIFO (para saber cuál murió primero) accedida concurrentemente por varios `SnakeRunner`. Se sustituyó una lista simple por `ConcurrentLinkedQueue<Snake> deadSnakes`, que permite `offer()`/`peek()` sin bloqueos explícitos (usa CAS internamente), evitando contención.
- **Estructuras compartidas del tablero (`mice`, `obstacles`, teleports/turbo)**: se accede a ellas dentro de bloques `synchronized` en `Board.step(...)`, ya que las mutaciones (comer ratón → crecer + nuevo obstáculo) deben ser atómicas respecto a otras serpientes moviéndose en el mismo tick.

### 3. Esperas activas eliminadas y mecanismo utilizado

- Se identificó el riesgo de implementar la pausa como `while (isPaused) { }` (espera activa, 100% de CPU por hilo virtual).
- Se reemplazó por **espera pasiva** usando el patrón monitor de Java: un objeto `pauseLock`, con `pauseLock.wait()` dentro de un `while (state == PAUSED)` (para protegerse de *spurious wakeups*) y `pauseLock.notifyAll()` en `resume()` para despertar a todas las serpientes a la vez.
- Cada `SnakeRunner` llama a `clock.checkPaused()` al inicio de cada ciclo, de forma que un hilo pausado libera la CPU por completo hasta que se notifica la reanudación.

### 4. Regiones críticas definidas y justificación de su alcance mínimo

- **`Snake`**: la región crítica es únicamente el acceso a `body` (dentro de `advance()`, `snapshot()`, `head()`, `size()`); el cálculo de la nueva posición y la lógica de movimiento se hacen **fuera** del bloque sincronizado para no retener el lock más de lo necesario.
- **`GameClock`**: el `synchronized` solo envuelve la espera/notificación sobre `pauseLock`, no la lógica de cada tick del juego.
- **`Board`**: cada operación de tablero (`step`) sincroniza solo la porción que lee/escribe estructuras compartidas (ratones, obstáculos, cola de muertos); la decisión de dirección de cada serpiente (`SnakeRunner.maybeTurn()`) es local al hilo y no requiere lock.
- Mantener las regiones críticas mínimas evita que, con N alto (20+ serpientes), el juego se vuelva secuencial de facto por bloqueos amplios.

### 5. Evidencias

> _Agregar aquí las capturas/gifs solicitados por el profesor antes de subir el commit final:_
> - Ejecución con varias serpientes (`-Dsnakes=4` o más).
> - Pausa mostrando de forma consistente la serpiente más larga y la peor serpiente.
> - Ejecución con N alto (`-Dsnakes=20`+) corriendo sin excepciones de concurrencia.
>
> Sugerencia: guardar las imágenes en `docs/evidencias/` dentro del repo y enlazarlas aquí, por ejemplo:
> `![Pausa con estadísticas](docs/evidencias/pausa-estadisticas.png)`

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**