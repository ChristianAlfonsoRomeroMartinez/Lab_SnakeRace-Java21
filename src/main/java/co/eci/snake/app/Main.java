package co.eci.snake.app;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;
import co.eci.snake.ui.legacy.SnakeApp;

import java.util.ArrayList;
import java.util.List;

public final class Main {
  private Main() {
  }

  public static void main(String[] args) {

    // GameState es un SINGLETON que mantiene el estado global de pausa.
    // Todos los hilos comparen el mismo monitor de sincronización.
    GameState gameState = new GameState();

    int numSnakes = getNumSnakes(args);

    // Crear el tablero
    // El Board contiene la lógica de movimiento.
    // Se crea en Main (no en SnakeApp)
    int width = 60;
    int height = 40;
    Board board = new Board(width, height);

    // Crear lista para mantener referencias a los hilos
    // Necesitamos llamar join() en main() para esperar a que terminen.
    // Cierre ordenado del programa.
    List<Thread> snakeThreads = new ArrayList<>();

    // Crear cada serpiente con su SnakeRunner (hilo virtual)
    for (int i = 0; i < numSnakes; i++) {
      // Posición inicial diferente para cada serpiente (evita colisiones iniciales)
      int x = (width / (numSnakes + 1)) * (i + 1);
      int y = height / 2;
      Direction initialDir = Direction.RIGHT;

      // Snake.of() es el factory method público para crear serpientes.
      Snake snake = Snake.of(x, y, initialDir);
      board.addSnake(snake);

      // SnakeRunner llama gameState.awaitIfPaused() para esperar señales.
      SnakeRunner runner = new SnakeRunner(snake, board, gameState);

      Thread thread = Thread.ofVirtual().start(runner);
      snakeThreads.add(thread);
    }

    // Lanzar la UI pasando componentes inyección de dependencias
    SnakeApp.launch(gameState, board);

    // Esperar a que terminen todos los hilos de las serpientes.
    try {
      for (Thread t : snakeThreads) {
        t.join();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Main thread interrupted: " + e.getMessage());
    }
  }

  // Método auxiliar para extraer número de serpientes desde argumentos.

  private static int getNumSnakes(String[] args) {
    int numSnakes = 2; // Default

    Integer prop = Integer.getInteger("snakes");
    if (prop != null) {
      numSnakes = prop;
    } else {
      for (String arg : args) {
        if (arg.startsWith("--snakes=")) {
          try {
            numSnakes = Integer.parseInt(arg.substring("--snakes=".length()));
          } catch (NumberFormatException e) {
            System.err.println("Invalid snakes argument: " + e.getMessage());
            numSnakes = 2;
          }
        }
      }
    }

    if (numSnakes <= 0) {
      System.err.println("Snakes count must be > 0, using default: 2");
      numSnakes = 2;
    }

    return numSnakes;
  }
}
