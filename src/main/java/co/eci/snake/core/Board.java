package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class Board {
  private final int width;
  private final int height;

  // CopyOnWriteArrayList para serpientes
  private final List<Snake> snakes = new CopyOnWriteArrayList<>();

  // Lock separado solo para items. NO usar synchronized en todo Board
  private final Object itemsLock = new Object();

  private final Set<Position> mice = new HashSet<>();
  private final Set<Position> obstacles = new HashSet<>();
  private final Set<Position> turbo = new HashSet<>();
  private final Map<Position, Position> teleports = new HashMap<>();

  public enum MoveResult {
    MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED
  }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0)
      throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i = 0; i < 6; i++)
      mice.add(randomEmpty());
    for (int i = 0; i < 4; i++)
      obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++)
      turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  //  Sincronización mínima en getters de UI

  public Set<Position> mice() {
    synchronized (itemsLock) {
      return new HashSet<>(mice);
    }
  }

  public Set<Position> obstacles() {
    synchronized (itemsLock) {
      return new HashSet<>(obstacles);
    }
  }

  public Set<Position> turbo() {
    synchronized (itemsLock) {
      return new HashSet<>(turbo);
    }
  }

  public Map<Position, Position> teleports() {
    synchronized (itemsLock) {
      return new HashMap<>(teleports);
    }
  }

  // Región crítica MÍNIMA en step()

  public MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    var head = snake.head();
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    // Variables para decisiones (calculadas dentro del lock)
    boolean hitObstacle;
    boolean ateMouse;
    boolean ateTurbo;
    boolean teleported = false;

    // REGIÓN CRÍTICA MÍNIMA: Solo proteger acceso a colecciones compartidas
    synchronized (itemsLock) {
      // Verificar obstáculo
      hitObstacle = obstacles.contains(next);
      if (hitObstacle)
        return MoveResult.HIT_OBSTACLE;

      // Verificar teleport
      if (teleports.containsKey(next)) {
        next = teleports.get(next);
        teleported = true;
      }

      // Comer ratón (operación atómica: remove + add)
      ateMouse = mice.remove(next);
      ateTurbo = turbo.remove(next);

      // Si comió ratón, agregar nuevo ratón y obstáculo
      if (ateMouse) {
        mice.add(randomEmpty());
        obstacles.add(randomEmpty());
        if (ThreadLocalRandom.current().nextDouble() < 0.2)
          turbo.add(randomEmpty());
      }
    } // FIN REGIÓN CRÍTICA

    // Movimiento de serpiente FUERA del lock
    snake.advance(next, ateMouse);

    // Retornar resultado basado en lo que pasó
    if (ateTurbo)
      return MoveResult.ATE_TURBO;
    if (ateMouse)
      return MoveResult.ATE_MOUSE;
    if (teleported)
      return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width * height * 2)
        break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }

  // Método addSnake sin sincronización

  public void addSnake(Snake snake) {
    Objects.requireNonNull(snake, "snake cannot be null");
    snakes.add(snake);
  }

  // Método snakes() sin sincronización

  public List<Snake> snakes() {
    return List.copyOf(snakes); // Retorna vista inmutable adicional por seguridad
  }
}
