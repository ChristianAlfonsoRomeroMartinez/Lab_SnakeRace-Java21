package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Snake {

  // ReadWriteLock permite múltiples lectores simultáneos (UI puede leer mientras
  // otras serpientes también leen) pero solo un escritor a la vez. Esto minimiza
  // contención:
  // la UI NO bloquea a otras UIs, solo bloquea cuando la serpiente se mueve.
  private final ReadWriteLock bodyLock = new ReentrantReadWriteLock();

  private final Deque<Position> body = new ArrayDeque<>();

  // Volatile
  private volatile Direction direction;

  private int maxLength = 5;

  private Snake(Position start, Direction dir) {
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(new Position(x, y), dir);
  }

  // direction es volatile. La lectura es atómica y siempre ve el valor más
  // reciente.
  public Direction direction() {
    return direction;
  }

  // turn() sin lock
  public void turn(Direction dir) {
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  // bloquea si advance() está escribiendo.
  public Position head() {
    bodyLock.readLock().lock();
    try {
      return body.peekFirst();
    } finally {
      bodyLock.readLock().unlock();
    }
  }

  public Deque<Position> snapshot() {
    bodyLock.readLock().lock();
    try {
      return new ArrayDeque<>(body);
    } finally {
      bodyLock.readLock().unlock();
    }
  }

  // WRITE lock bloquea todas las lecturas durante la modificación.

  public void advance(Position newHead, boolean grow) {
    bodyLock.writeLock().lock();
    try {
      body.addFirst(newHead);
      if (grow)
        maxLength++;
      while (body.size() > maxLength)
        body.removeLast();
    } finally {
      bodyLock.writeLock().unlock();
    }
  }
}
