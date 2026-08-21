package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class Snake {

  private final int id;
  private final Deque<Position> body = new ArrayDeque<>();
  private volatile Direction direction;
  private int maxLength = 5;
  private volatile boolean alive = true;
  private volatile long deathTimestamp = -1;

  private Snake(int id, Position start, Direction dir) {
    this.id = id;
    this.body.addFirst(Objects.requireNonNull(start, "start"));
    this.direction = Objects.requireNonNull(dir, "dir");
  }

  public static Snake of(int id, int x, int y, Direction dir) {
    return new Snake(id, new Position(x, y), dir);
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(0, new Position(x, y), dir);
  }

  public int getId() {
    return id;
  }

  public boolean isAlive() {
    return alive;
  }

  public long getDeathTimestamp() {
    return deathTimestamp;
  }

  public synchronized boolean markDead() {
    if (this.alive) {
      this.alive = false;
      this.deathTimestamp = System.nanoTime();
      return true;
    }
    return false;
  }

  public Direction direction() {
    return direction;
  }

  public void turn(Direction dir) {
    if (dir == null || !alive) return;
    Direction cur = this.direction;
    if ((cur == Direction.UP && dir == Direction.DOWN) ||
        (cur == Direction.DOWN && dir == Direction.UP) ||
        (cur == Direction.LEFT && dir == Direction.RIGHT) ||
        (cur == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public synchronized Position head() {
    return body.peekFirst();
  }

  public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
  }

  public synchronized int size() {
    return body.size();
  }

  public synchronized void advance(Position newHead, boolean grow) {
    if (!alive) return;
    body.addFirst(Objects.requireNonNull(newHead, "newHead"));
    if (grow) maxLength++;
    while (body.size() > maxLength) {
      body.removeLast();
    }
  }
}
