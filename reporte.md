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

### Protege solo las regiones críticas estrictamente necesarias (evita bloqueos amplios).

#### **Board.java**
- **Riesgo**
El arraylist no puede asegurar consistencia cuando muchos hilos lo usan al tiempo, ademas tenemos la particularidad que solo al inicio de la ejecucion se escribe

- **Solucion**
Usamos CopyOnWriteArrayList permite lecturas sin bloqueo, ademas crea una copia interna automatica lo que nos permite que los lectores nunca vean los datos de forma inconsistente, este cambio en bueno porque tenemos pocas escrituras y muchas lecturas

- **Riesgo**
1. Comer ela misma galleta simultáneamente 
2. Agregar obstaculos al mismo tiempo
3. Se puede leer el tablero mientras se mmodifica

- **Solucion**
Usamos Lock para los items, no uso synchronized porque puede generar bloqueos en los hilos, esto porque sincronizar todo el board generaria bloqueos en todas las serpientes

- **Riesgo**
La GUI llama varios metodos constantemente como mice() o obstacles(), cada 16ms, en caso de usar synchronized podemos tener bloqueos mortales

- **Solucion**
Usamos lock para copiar las colecciones para la region critica minima new HashSet<>(mice), por lo que el bloqueo se reduce en orden de mricosegundos a milisegundos

- **Riesgo**
Teniamos synchronized en el metodo step() como region crita, aca cuando se mueve una serpiente las otras deben esparar, lo que es supremamente inficiente

- **Solucion**
Solo bloqueamos las modificaciones a los items de mice, obstavles y turbo, y snake.advance() se ubica fuera del lock.
Esto se materializa en que cuando las serpientes accedan a items mientras se actualiza su cuerpo

- **Riesgo**
addSnake() y snakes() usan sincronizacion
- **Solucion**
Gracias a la implementacion de CopyOnWriteArrayList podemos evitar el synchronized y a su vez evitamos bloqueos mortales


#### **Snake.java**
- **Riesgo**
La estructura de datos ArrayDeque no es thread-safe con los objetos GamePanel, esta lee el body mientras SnakeRunner cada 40 o 80 milisegundos, por lo que en esencia ve una parte de la serpiente actiualizada y la otra en un estado anterior 

- **Solucion**
Uso ReadWriteLock, ya que permite la lectura simultanea por varios hilos, por lo que mientras la GUI lee, los hilos tambien lo hacen,
Por otro lado solo bloquemaos al escribir con el metodo advance()

- **Riesgo**
En los cambios de direccion no son visibles inmediatamente, por lo que puede continuar en la direccion anterior por frames

- **Solucion**
Es necesario la incoorporacion de volatile en diretion nos garantiza que todos los hilos vean el cambio en una operacion atomica.

- **Riesgo**
La GUI puede ver el tablero mientras se modifica, por lo que puede tomar datos incosistentes 

- **Solucion**
WRITE lock bloquea todas las lecturas durante la modificación. 
Región crítica: operaciones (addFirst, quitar, remover). Solo bloqueamos durante la modificación del Deque, NO durante el cálculo de newHead o la lógica de negocio.

## 3) Control de ejecución seguro (UI) 

- **Pausa sin tearing**: En la UI se pausa primero y se espera brevemente (100 ms) para que todos los hilos alcancen awaitIfPaused() asi los datos tomados no estan a medias.

- **Snapshot inmutable**: Agrege un Stats record inmutable en Board con el estado completo (vivas, muertas, choques, serpiente viva más larga y primera muerta). La UI consume ese snapshot para mantener consistencia visual.

- **Conteo de choques y eliminación**: Al detectar colisión con otra serpiente se incrementa un contador  y se registra la muerte, La serpiente se elimina del tablero y su hilo termina, evitando que quede congelada en pantalla.

- **Render dinámico de serpientes**: GamePanel obtiene la lista viva desde board::snakes en cada repaint. Esto respeta el número correcto de serpientes creadas y elimina inmediatamente las muertas de la vista.


- **Posiciones iniciales sin colisión**: En Main añadi un generador de posiciones findFreeStart para no generar muertes inmediatas al inicio por solapamiento.