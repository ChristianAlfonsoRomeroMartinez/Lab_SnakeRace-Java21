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

en main siguiendo el patrón MVC: Main = Orquestador, SnakeApp = Vista.
    