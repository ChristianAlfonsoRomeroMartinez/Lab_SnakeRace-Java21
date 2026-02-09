# Parte II — SnakeRace concurrente (núcleo del laboratorio)

## 1) Análisis de concurrencia
1. Cada serpiente toma su hilo y es autonoma, porque la aleatoriedad es completamente independiente, ejecuta SnakeRunner por cada serpiente.
  Cada hilo tiene un loop donde se decide si girar aleatoriamente, adicional a lo anterior cuentan con sleep, donde se define cuanto tiempo entra a reposo, y si esta en turbo o normal
2. 
- Condiciones de carrera
    Tenemos condiciones de carrera en las estructuras compartidas que no cuentan con sincronizacion, por lo que 2 serpientes pueden comer la misma galleta y generar inconsistencias

- Colecciones No Seguras
    Las colecciones son thread-unsafe por lo que los hilos pueden modificar las estructuras y quedar en estado inconsistente, por lo que es preciso usar sincronice

- Espera activa
    Los hilos no se pausan correctamente, puesto que se sigue ejecutando en la pausa por lo que no se detienen con sleep.


## 2) Correcciones mínimas y regiones críticas   

### Esperas activas reemplazándolas por señales / estados o mecanismos de la librería de concurrencia

#### **GameState.java**
- **Riesgo**
La pausa solo se implementaba con lectura por lo que teniamos espera activa y no se veian entre los distintos hilos

- **Solucion**
Cambie la clase de enum a una clase donde se incorporo un monitor, la pausa volatile para asegurar que todos los hilos lo ven, por otro lado se integro pauselock, lo que quiere decir que cada que un hilo entre en zona critica nadie mas entra, lo que permite asegurar la integridad de los datos.
Tambien se integro **awaitIfPaused()** lo que nos permite que los hilos se detengan en pausa, **wait** por lo que el hilo suspendido no consume CPU, y por ultimo **notifyall()** que es el que notifica a todos los hilos que ya no se esta en pausa


#### **SnakeRunner.java**
- **Riesgo**
Las serpientes seguian en ejecucion apesar que se el estado fuera pausa

- **Solucion**
Integramos la solucion dada en GameState.java en especifico al metodo **gameState.awaitIfPaused()** por lo que podemos bloquear los hilos correctamente

#### **GameClock.java**
- **Riesgo**
No habia una responsabilidad clara, por lo que se podian generar incoherencias, ademas de leer valores errados y seguir avanzando cuando no deberia.

- **Solucion**
Primero tomo el estado del juego de gameState,isPaused() ya que todo pasa por GameState. tambien tuve que hacer un enum local, ya que por las modificaciones que hice en **GameState.java** debia asegurar los Running y Stopped

#### **Main.java**
- **Riesgo**
No habia una clara distincion de responsabilidades, y se le adjuntaban responsabilidades a app de GUI que no deberia tener, ademas evidencie inyeccion del estado inconsistente

- **Solucion**
Main orquesta todo: crea GameState, Board, serpientes, SnakeRunner con estado compartido, GameClock, y luego pasa dependencias a SnakeApp. por lo que se logro la pausa global y unica. Basandome en el modelo vista controlador

#### **SnakeApp.java**
- **Riesgo**
No habia una clara distincion de responsabilidades, y se le adjuntaban responsabilidades a app de GUI que no deberia tener, ademas evidencie inyeccion del estado inconsistente

- **Solucion**
Alineado con el cambio realizado en Main, ahora recibimos en Board y GameClock desde Main ya que se define como orquestador, esto nos permite un estado real y una GUI consistente con lo que esta pasando

#### **Board.java**
- **Riesgo**
No habia una clara distincion de responsabilidades, y se le adjuntaban responsabilidades a app de GUI que no deberia tener, ademas evidencie inyeccion del estado inconsistente

- **Solucion**
Agregue un registro para agregar addSnake y snakes() para asi saber exactamente las servientes y dibujarlas correctamente