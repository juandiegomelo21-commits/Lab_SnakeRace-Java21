package co.eci.snake;

import co.eci.snake.core.GameState;
import co.eci.snake.core.engine.GameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GameClockTest {

    @Test
    @DisplayName("Coordinacion de pausa y reanudacion con wait y notify")
    void testPauseAndResume() throws InterruptedException {
        try (GameClock clock = new GameClock(50, () -> {})) {
            clock.start();
            assertEquals(GameState.RUNNING, clock.getState());

            clock.pause();
            assertEquals(GameState.PAUSED, clock.getState());
            assertTrue(clock.isPaused());

            CountDownLatch waitingLatch = new CountDownLatch(1);
            CountDownLatch resumedLatch = new CountDownLatch(1);
            AtomicBoolean threadProceeded = new AtomicBoolean(false);

            var exec = Executors.newVirtualThreadPerTaskExecutor();
            exec.submit(() -> {
                try {
                    waitingLatch.countDown();
                    clock.checkPaused();
                    threadProceeded.set(true);
                    resumedLatch.countDown();
                } catch (InterruptedException ignored) {}
            });

            waitingLatch.await(2, TimeUnit.SECONDS);
            Thread.sleep(100);

            assertFalse(threadProceeded.get(), "El hilo debe permanecer suspendido durante la pausa");

            clock.resume();
            assertEquals(GameState.RUNNING, clock.getState());
            assertFalse(clock.isPaused());

            boolean resumed = resumedLatch.await(2, TimeUnit.SECONDS);
            assertTrue(resumed, "El hilo debe continuar tras llamar resume()");
            assertTrue(threadProceeded.get());
        }
    }
}
