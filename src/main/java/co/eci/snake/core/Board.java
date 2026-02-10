package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

public final class Board {
  private final int width;
  private final int height;

  // CopyOnWriteArrayList para serpientes
  private final List<Snake> snakes = new CopyOnWriteArrayList<>();

  // Registro de muertes y colisiones entre serpientes
  private final List<DeadSnake> deadSnakes = new CopyOnWriteArrayList<>();
  private final AtomicInteger deathCounter = new AtomicInteger(0);
  private final AtomicInteger collisionCounter = new AtomicInteger(0);

  // Lock separado solo para items. NO usar synchronized en todo Board
  private final Object itemsLock = new Object();

  private final Set<Position> mice = new HashSet<>();
  private final Set<Position> obstacles = new HashSet<>();
  private final Set<Position> turbo = new HashSet<>();
  private final Map<Position, Position> teleports = new HashMap<>();

  public enum MoveResult {
    MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED, DEAD_BY_OTHER, DEAD_BY_SELF
  }

  // REQ-UI: Registro inmutable de muerte (thread-safe por diseño)
  public record DeadSnake(Snake snake, int length, int deathOrder, Instant deathTime, int snakeIndex) {}

  // REQ-UI: Snapshot inmutable de estadísticas para evitar tearing
  public record Stats(int aliveCount, int deadCount, int collisionCount, Snake longestAlive, DeadSnake firstDead) {}

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

  // Sincronización mínima en getters de UI

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

    // Detectar colisiones ANTES del movimiento

    for (Snake other : snakes) {
      var body = other.snapshot(); // ReadLock en Snake
      if (other == snake) {
        boolean first = true;
        for (Position p : body) {
          if (first) {
            first = false; // saltar la cabeza actual
            continue;
          }
          if (p.equals(next)) {
            return MoveResult.DEAD_BY_SELF;
          }
        }
      } else {
        if (body.contains(next)) {
          return MoveResult.DEAD_BY_OTHER;
        }
      }
    }

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

  //  Registrar muerte y retirar serpiente del tablero
  public void killSnake(Snake snake, boolean collidedWithOther) {
    Objects.requireNonNull(snake, "snake cannot be null");
    int order = deathCounter.incrementAndGet();
    if (collidedWithOther) {
      collisionCounter.incrementAndGet();
    }
    int length = snake.snapshot().size();
    int indexAtDeath = snakes.indexOf(snake);
    deadSnakes.add(new DeadSnake(snake, length, order, Instant.now(), indexAtDeath));
    snakes.remove(snake); // al remover, la UI deja de dibujarla
  }

  // REQ-UI: Snapshot consistente para UI (sin tearing)
  public Stats getStats() {
    Snake longestAlive = null;
    int maxLen = 0;
    for (Snake s : snakes) {
      int len = s.snapshot().size();
      if (len > maxLen) {
        maxLen = len;
        longestAlive = s;
      }
    }

    DeadSnake firstDead = null;
    for (DeadSnake ds : deadSnakes) {
      if (firstDead == null || ds.deathOrder() < firstDead.deathOrder()) {
        firstDead = ds;
      }
    }

    return new Stats(snakes.size(), deadSnakes.size(), collisionCounter.get(), longestAlive, firstDead);
  }
}
