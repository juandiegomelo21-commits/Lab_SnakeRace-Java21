package co.eci.snake;

import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SnakeTest {

    @Test
    @DisplayName("Impedir giros opuestos de 180 grados")
    void testInvalidTurn() {
        Snake snake = Snake.of(1, 5, 5, Direction.UP);
        snake.turn(Direction.DOWN);
        assertEquals(Direction.UP, snake.direction());

        snake.turn(Direction.RIGHT);
        assertEquals(Direction.RIGHT, snake.direction());

        snake.turn(Direction.LEFT);
        assertEquals(Direction.RIGHT, snake.direction());
    }

    @Test
    @DisplayName("Gestion de estado y muerte de la serpiente")
    void testSnakeLifecycle() {
        Snake snake = Snake.of(1, 10, 10, Direction.RIGHT);
        assertTrue(snake.isAlive());
        assertEquals(-1, snake.getDeathTimestamp());

        boolean marked = snake.markDead();
        assertTrue(marked);
        assertFalse(snake.isAlive());
        assertTrue(snake.getDeathTimestamp() > 0);

        boolean secondMark = snake.markDead();
        assertFalse(secondMark);
    }

    @Test
    @DisplayName("Lecturas y escrituras concurrentes sin ConcurrentModificationException")
    void testConcurrentReadAndWrite() throws InterruptedException {
        Snake snake = Snake.of(1, 0, 0, Direction.RIGHT);
        int iterations = 10_000;
        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        var executor = Executors.newFixedThreadPool(2);

        // Hilo escritor
        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    snake.advance(new Position(i % 50, (i + 1) % 50), i % 10 == 0);
                }
            } catch (Exception e) {
                errorOccurred.set(true);
            } finally {
                latch.countDown();
            }
        });

        // Hilo lector
        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    var snapshot = snake.snapshot();
                    assertNotNull(snapshot);
                    assertTrue(snake.size() > 0);
                }
            } catch (Exception e) {
                errorOccurred.set(true);
            } finally {
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished);
        assertFalse(errorOccurred.get(), "No deben ocurrir excepciones de modificacion concurrente");
    }
}
