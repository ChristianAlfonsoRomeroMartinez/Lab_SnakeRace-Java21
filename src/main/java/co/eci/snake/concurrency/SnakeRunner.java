package co.eci.snake.concurrency;

import java.util.concurrent.ThreadLocalRandom;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Snake;

public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;

  // Nos da las senales de pausa
  private final GameState gameState;

  public SnakeRunner(Snake snake, Board board, GameState gameState) {
    this.snake = snake;
    this.board = board;
    this.gameState = gameState;
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {

        // Senal de pausa
        gameState.awaitIfPaused();
        maybeTurn();
        var res = board.step(snake);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        } else if (res == Board.MoveResult.DEAD_BY_OTHER || res == Board.MoveResult.DEAD_BY_SELF) {
          // Registrar muerte, incrementar contador terminar hilo
          board.killSnake(snake, res == Board.MoveResult.DEAD_BY_OTHER);
          break;
        }
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0)
          turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p)
      randomTurn();
  }

  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
