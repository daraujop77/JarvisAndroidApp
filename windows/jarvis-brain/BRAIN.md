# JARVIS — Cerebro (diseño)

Modelo vivo del mark actual de `JarvisOrb.kt` (círculo + semicírculos), pasado a 3D.
Es un **campo**, no una animación en bucle. El mismo estado nunca produce la misma pose.

Contrato compartido: el teléfono (Filament) y el PC (este visor, Three.js) leen el mismo
`BrainDrive`. Ninguno de los dos inventa movimiento propio.

## Por qué el orbe de hoy se ve mecánico

`JarvisOrb.kt` gira dos anillos con `LinearEasing` a periodo fijo (9 s y 14 s) y un
pulso que va de 0.86 a 1.06. Eso es determinista y **periódico**: a los 9 s el anillo
exterior está exactamente donde empezó. Un ser vivo no cierra el ciclo.

## El campo

Tres ángulos avanzan a velocidades **inconmensurables** (1 : √2 : π/2). Su combinación
no tiene periodo, así que la pose no se repite aunque el modo no cambie.

```
a = t * 0.37
b = t * 0.37 * sqrt(2)
c = t * 0.37 * pi/2
```

`t` no es el reloj de pared: está deformado por `tempo`. Esperar y pensar son el mismo
campo visto a distinta velocidad, no dos clips distintos.

## BrainDrive

Cinco números, todos 0..1 salvo `tempo`. El renderer solo hace ease hacia ellos
(`1 - e^(-dt*2.4)`); nunca salta.

| Canal | Qué mueve |
| --- | --- |
| `energy` | Brillo del núcleo, amplitud de la membrana, intensidad de luz |
| `focus` | Los filamentos colapsan hacia el centro; los anillos se acercan al ecuador |
| `tempo` | Velocidad del campo. No cambia la forma, cambia el tiempo |
| `coherence` | Alto = ordenado, bandas limpias. Bajo = los anillos se sueltan y cabecean |
| `warmth` | El núcleo va de cian a blanco; al hablar, los filamentos se juntan en láminas |

## Modos

| Modo | energy | focus | tempo | coherence | Se lee como |
| --- | --- | --- | --- | --- | --- |
| IDLE | 0.28 | 0.15 | 0.55 | 0.35 | Respira, deriva, no pide nada |
| LISTENING | 0.55 | 0.72 | 0.90 | 0.62 | Se recoge, atento, orientado |
| THINKING | 1.00 | 0.16 | 1.70 | 0.05 | Rápido, brillante, membrana agitada, anillos sueltos |
| SPEAKING | 0.70 | 0.84 | 1.05 | 0.78 | Caliente, agrupado, con pulso de voz |
| WAITING | 0.18 | 0.30 | 0.32 | 0.50 | Casi quieto, pero el campo no se detiene |
| OFFLINE | 0.06 | 0.00 | 0.12 | 0.90 | Frío, lento, apagado — no congelado |

`SPEAKING` acepta además el nivel de audio real en `warmth` (0..1 por frame). Así la voz
de verdad empuja el cerebro, no un metronomo.

## Paleta — una sola familia

Holograma de verdad = **monocromo**. La información la lleva la luminancia, no un
segundo color. Base cian `#3FD3EC`, pálido `#A9ECF7`, profundo `#0D5F74`.

- **Violeta** `#7C6CF0`: acento fino, solo en nodos muy activos y baja coherencia.
- **Cálido** `#FFB257`: solo al hablar, y poco.

Nada de arcoíris. Si se ven tres colores a la vez, está mal.

## Geometría — la marca, más el instrumento

Heredado de `JarvisOrb.kt`, mismos barridos: exterior 70°/55°/80°/40°, medio
120°/95°/45°, más un tercer anillo lejano casi de canto (profundidad).
Cada arco es un **tubo** metálico con clearcoat, no una cinta.

Lo que ya no es solo semicírculos:

| Pieza | Para qué |
| --- | --- |
| **Red neuronal** — 110 nodos Fibonacci, ~330 enlaces | Es el cerebro. Se ven los impulsos cruzar |
| **Jaula geodésica** (wireframe) | Estructura; sin ella es una bola de gas |
| **64 marcas de instrumento** | Lectura de máquina, no de adorno |
| **7 placas orbitales** planas | Rompen la silueta redonda; brillan de canto |
| **Cáscara holográfica** (shader) | Fresnel + líneas de barrido + banda que sube |
| **Núcleo + brasa** | Membrana que se deforma, brasa agitada dentro |

## El holograma

Shader propio: el borde brilla y el centro queda transparente (fresnel a la 4.5),
líneas de barrido ancladas al modelo — giran con él, no con la pantalla — y una
banda ancha que sube con periodo aperiódico. **FrontSide**, nunca DoubleSide: en
aditivo se suma dos veces y se convierte en una bola blanca.

## La red — cómo "piensa"

Cada disparo enciende un nodo a 1, sus vecinos a 0.7 y los vecinos de esos a 0.34:
un frente de onda de dos saltos. El decaimiento es lento, así que queda **rastro**
y se lee el camino, no un parpadeo. La frecuencia de disparo sigue a `energy`
(2/s en reposo, ~28/s pensando). El lector del HUD muestra los nodos vivos.

700 filamentos en órbitas irracionales (`i * π(3-√5)`) dan la sensación de actividad
interna. En teléfono bajar a ~200 si el frame lo pide.

## De dónde sale el drive (no del renderer)

El renderer no decide. Quien alimenta `BrainDrive`:

- **Android hoy:** `OrbActivity` ya existe (`IDLE / LISTENING / THINKING / OFFLINE`).
  Mapear esa enum a la fila de arriba. `SPEAKING` y `WAITING` se agregan.
- **Cuando haya PC-A:** el mismo drive puede venir de eventos reales — tokens/s del
  modelo sube `energy`, una tool en vuelo baja `coherence`, silencio de espera baja
  `tempo`. El mapping es una tabla, no código de animación.

## Presupuesto

- Un draw por frame. Sin recomposición alrededor (la ley que ya cumple el Canvas).
- `prefers-reduced-motion`: el campo se congela en la pose del modo, el núcleo queda
  fijo. Igual que `LocalReducedMotion` hoy.
- Teléfono: Filament consume el mismo contrato. Este visor es la referencia de PC y
  el lugar para juzgar si se siente vivo antes de portar.

## Qué no hacer

- No keyframear "pensar" como un clip de 3 s. Se nota el corte.
- No girar todo sobre un solo eje a velocidad constante. Es el fallo actual.
- No atar el brillo a un `sin(t)` único. El seno es el acento; el campo es el cuerpo.
