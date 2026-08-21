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

- **N serpientes** corren de forma autónoma (cada una en su propio hilo virtual).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero se repite en los bordes).

---

## Arquitectura

```
co.eci.snake
├─ app/                 # Punto de entrada de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position, GameState
├─ core/engine/         # GameClock (ticks periódicos, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica de ejecución con virtual threads)
└─ ui/legacy/           # Interfaz gráfica Swing con tablero y controles
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

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia
- Autonomía de serpientes mediante hilos virtuales de Java 21 (`Executors.newVirtualThreadPerTaskExecutor()`).
- Identificación de condiciones de carrera, colecciones no sincronizadas y esperas activas.

### 2) Correcciones mínimas y regiones críticas
- Eliminación de esperas activas mediante monitores y sincronización pasiva (`wait` / `notifyAll`).
- Protección de regiones críticas estrictamente necesarias sin bloqueos excesivos.

### 3) Control de ejecución seguro (UI)
- Coordinación de estados con `GameClock` y actualización consistente en el hilo de Swing (EDT).
- Cálculo sin inconsistencias (*tearing*) de la serpiente viva más larga y la primera en morir al pausar.

### 4) Robustez bajo carga
- Verificación con alto número de serpientes (`-Dsnakes=20`+) sin excepciones de concurrencia ni interbloqueos (*deadlocks*).

---

## Reporte de laboratorio

### Parte I — Sincronización en PrimeFinder (`wait` / `notifyAll`)

Para el ejercicio de calentamiento con `PrimeFinder`, el diseño de sincronización se estructuró de la siguiente forma:

1. **Monitor y cerrojo compartido**:
   Se definió un objeto de bloqueo compartido (`Object lock = new Object()`) entre el hilo coordinador de control y todos los hilos trabajadores de búsqueda de primos (`PrimeFinderThread`).

2. **Condición de suspensión y prevención de Spurious Wakeups**:
   Se utilizó una bandera booleana `paused`. Cada hilo de trabajo, antes de procesar el siguiente candidato a número primo, evalúa dicha condición dentro de un bloque sincronizado:
   ```java
   synchronized (lock) {
       while (paused) {
           lock.wait();
       }
   }
   ```
   El uso del ciclo `while` asegura que si el hilo despierta de forma espuria sin que la bandera `paused` haya cambiado a `false`, vuelva inmediatamente al estado de espera pasiva.

3. **Prevención de Lost Wakeups**:
   La comprobación de la condición y la invocación de `wait()` se encuentran protegidas bajo el mismo cerrojo (`synchronized(lock)`). De igual manera, cuando el hilo coordinador detecta que han transcurrido los `t` milisegundos (`Thread.sleep(t)`), establece `paused = true`. Al recibir la pulsación de la tecla `ENTER` en consola, actualiza `paused = false` y emite `lock.notifyAll()` dentro del bloque sincronizado:
   ```java
   synchronized (lock) {
       paused = false;
       lock.notifyAll();
   }
   ```
   Esto garantiza que ningún hilo de trabajo pierda la notificación por cambios de estado ocurridos fuera del monitor.

4. **Eliminación de espera activa**:
   Durante el tiempo que el programa espera la entrada del usuario, los hilos de trabajo liberan completamente los recursos de la CPU al permanecer en estado `WAITING`, evitando bucles de espera activa.

---

### Parte II — Concurrencia en SnakeRace

#### 1. Data races encontradas y solución aplicada

| Componente | Problema encontrado | Consecuencia | Solución aplicada |
| :--- | :--- | :--- | :--- |
| `Snake.java` | La colección `body` (`ArrayDeque<Position>`) era leída por el hilo de la interfaz gráfica (`snapshot()` / `head()`) mientras el hilo virtual de la serpiente mutaba su contenido (`advance()`). | `ConcurrentModificationException` o lecturas inconsistentes de la serpiente durante el dibujado en pantalla. | Se sincronizaron con `synchronized` todos los métodos de acceso y mutación de `body` (`advance`, `snapshot`, `head`, `size`). El método `snapshot()` retorna una copia defensiva atómica dentro de la sección crítica. Los campos `direction` y `alive` se declararon `volatile` para garantizar visibilidad inmediata entre hilos. |
| `GameClock.java` | El método `pause()` únicamente modificaba el estado interno para detener el repintado periódico, pero no existía ningún mecanismo para detener los hilos de las serpientes. | Las serpientes continuaban avanzando a máxima velocidad en segundo plano a pesar de que la interfaz gráfica aparentaba estar en pausa. | Se implementó el método `checkPaused()`, invocado por cada `SnakeRunner` antes de ejecutar cada paso. Si el juego está en pausa, el hilo entra en espera pasiva (`pauseLock.wait()`) hasta que `resume()` invoque `pauseLock.notifyAll()`. |
| `SnakeApp.java` | La lectura de las estadísticas (serpiente viva más larga y primera serpiente muerta) se realizaba de manera desacoplada sin garantizar un estado estático del juego. | Inconsistencia de datos (*tearing*): la interfaz podía mostrar información correspondiente a ticks diferentes de la simulación. | El cálculo de estadísticas se ejecuta en el hilo EDT de Swing (`SwingUtilities.invokeLater`) tras solicitar la pausa en el `GameClock`, asegurando que los hilos de las serpientes se encuentren detenidos y la instantánea sea consistente. |

#### 2. Colecciones y estructuras de datos concurrentes

- **`Snake.body` (`ArrayDeque<Position>`)**: Se mantuvo `ArrayDeque` por su alta eficiencia en inserciones en cabeza y eliminaciones en cola ($O(1)$), protegiendo todas las operaciones de lectura y modificación con métodos sincronizados de alcance mínimo.
- **Registro cronológico de muertes en `Board.java`**: Para registrar el orden exacto en el que mueren las serpientes (necesario para identificar a la "peor serpiente"), se utilizó una cola concurrente no bloqueante `ConcurrentLinkedQueue<Snake> deadSnakes`. El método `markDead()` en `Snake` cambia el estado de vida y registra la marca de tiempo de forma atómica (`boolean`), evitando duplicados al encolar.
- **Estructuras del tablero (`mice`, `obstacles`, `turbo`, `teleports`)**: El método `step(Snake snake)` en `Board` se encuentra sincronizado, garantizando que el consumo de ratones, la generación de obstáculos y el uso de teletransportes ocurran de forma atómica.

#### 3. Esperas activas eliminadas y mecanismo utilizado

- Se evitó el uso de bucles de sondeo continuo (`while (isPaused) { }`), los cuales consumirían el 100% de CPU por cada hilo virtual activo.
- Se implementó un esquema de sincronización pasiva con el monitor `pauseLock` en `GameClock`. Al pausar el juego, los hilos de las serpientes se bloquean en `pauseLock.wait()`. Al reanudar, `pauseLock.notifyAll()` despierta a todas las serpientes simultáneamente.
- El bucle `while (state.get() == GameState.PAUSED)` en `checkPaused()` previene despertares espurios y asegura que la condición de pausa siga vigente antes de reanudar el avance.

#### 4. Regiones críticas definidas y justificación de alcance mínimo

- **En `Snake`**: La sincronización se limita estrictamente a las operaciones sobre `body` (`advance`, `head`, `snapshot`, `size`). El cálculo de la nueva dirección o la validación de giros de 180° se realizan fuera de la sección crítica.
- **En `GameClock`**: El monitor `pauseLock` solo bloquea durante el cambio de estado de pausa y reanudación, sin interferir con la programación de ticks del temporizador.
- **En `Board`**: La sección crítica sincronizada abarca únicamente la actualización atómica del estado del tablero en `step(...)`. La toma de decisiones y cálculos probabilísticos de giro en `SnakeRunner.maybeTurn()` se ejecutan localmente en cada hilo sin bloquear el tablero.
- Mantener las secciones críticas con alcance mínimo permite que el sistema escale eficientemente cuando se configuran múltiples serpientes (20 o más), evitando que la ejecución se serialice por contención de bloqueos.

---

### Evidencias de ejecución

> Para completar la entrega, adjuntar las capturas en la carpeta `docs/evidencias/` y referenciarlas a continuación:
> - Ejecución del juego con múltiples serpientes (`-Dsnakes=4`).
> - Juego en pausa mostrando de forma consistente la serpiente viva más larga y la primera en morir.
> - Ejecución bajo carga alta (`-Dsnakes=20` o superior) demostrando estabilidad sin excepciones de concurrencia.

---

## Criterios de evaluación

- (3) **Concurrencia correcta**: sin condiciones de carrera; sincronización localizada y segura.
- (2) **Pausa/Reanudar**: consistencia visual y de estado sin esperas activas.
- (2) **Robustez**: ejecución estable con N alto sin excepciones de concurrencia.
- (1.5) **Calidad**: código estructurado, limpio y sin code smells.
- (1.5) **Documentación**: reporte de laboratorio claro y completo.

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye la compilación y ejecución de la suite de pruebas unitarias y de concurrencia.

---

## Créditos

Laboratorio de Arquitecturas de Software (ARSW) — Escuela Colombiana de Ingeniería Julio Garavito.
Adaptación modernizada con Java 21 y Virtual Threads sobre la base construida por el Ing. Javier Toquica.