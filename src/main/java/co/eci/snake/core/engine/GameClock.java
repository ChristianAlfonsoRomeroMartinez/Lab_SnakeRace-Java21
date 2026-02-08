package co.eci.snake.core.engine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import co.eci.snake.core.GameState;

public final class GameClock implements AutoCloseable {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final AtomicReference<ClockState> state = new AtomicReference<>(ClockState.STOPPED);

  private final GameState gameState;

  //El enum ahora esta en local
  private enum ClockState { STOPPED, RUNNING }


  public GameClock(long periodMillis, Runnable tick, GameState gameState) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = java.util.Objects.requireNonNull(tick, "tick");
    this.gameState = java.util.Objects.requireNonNull(gameState, "gameState");
  }

  public void start() {
    if (state.compareAndSet(ClockState.STOPPED, ClockState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == ClockState.RUNNING&& !gameState.isPaused()) {
          tick.run();
        }
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  //Game state se encarga de pausar
  public void pause()  { gameState.pause(); }
  public void resume() { gameState.resume(); }
  //public void pause()  { state.set(GameState.PAUSED); }
  //public void resume() { state.set(GameState.RUNNING); }
  public void stop()   { state.set(ClockState.STOPPED); }
  @Override public void close() { scheduler.shutdownNow(); }
}
