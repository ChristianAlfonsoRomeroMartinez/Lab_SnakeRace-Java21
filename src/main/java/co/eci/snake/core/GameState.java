package co.eci.snake.core;

//Requerimos un mecanismo de espera por lo que se integra una clase
//public enum GameState { STOPPED, RUNNING, PAUSED }

public final class GameState {
    //estado del jeugo
    private volatile boolean paused = false;
    //monitor de espera
    private final Object pauseLock = new Object();


    //Metodo para la pausa
    public boolean isPaused() {
        return paused;
    }


    //En pausa continuen los hilos suspendidos
    public void pause() {
        synchronized (pauseLock) {
            paused = true;
        }
    }

    //Reanudar la ejecucion de los hilos
    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll(); //Notificamos a los hilos
        }
    }

    public void awaitIfPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) { //Notifica constantemente a los hilos, impidiendo que se ejecuten
                pauseLock.wait();
            }
        }
    }
}