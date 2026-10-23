# Launcher para la radio ADAYO AC822X

Un launcher hecho a medida para la radio **ADAYO AC822X** (la del Suzuki Swift): Android 4.2.2,
pantalla de 800 × 480, 850 MB de RAM y un procesador de 4 núcleos Cortex‑A7 a 1 GHz.
Reemplaza la pantalla de inicio de fábrica por una más liviana y pensada para manejar.

## Antes y ahora

| Antes: launcher de fábrica de ADAYO | Ahora: este launcher |
|---|---|
| ![Pantalla de inicio original de ADAYO](docs/antes.png) | ![Pantalla de inicio de este launcher](docs/despues.png) |

| Todas las apps | Apps abiertas |
|---|---|
| ![Todas las apps](docs/todas.png) | ![Apps abiertas con la RAM de cada una](docs/abiertas.png) |

## Qué mejora

- **La velocidad, en grande.** Lo que se mira de reojo al manejar: km/h del GPS con los satélites al lado.
  Sin señal muestra "–", nunca una velocidad vieja.
- **Botones grandes**, cómodos con el auto en movimiento: cuatro principales (Waze, YouTube, Radio, Teléfono)
  y cuatro chicos (Música BT, USB, Ecualizador, Todas las apps). **Manteniendo apretado** un botón grande se
  elige qué app abre.
- **Apps abiertas:** cuántas hay, cuánta RAM usa cada una, y un botón para cerrarlas (una o todas).
- **Luna:** oscurece la pantalla de inicio cuando el brillo molesta de noche. Otro toque, normal.
- **Sin ruido de FM al dar contacto:** la radio retoma la última fuente del sistema. Si quedó en FM aparece un
  chip rojo; un toque la deja en Bluetooth, que arranca en silencio.
- **Liviano:** sin widgets, sin fondo de pantalla, sin base de datos y sin librerías.

## Consumo

| | Antes (launcher de ADAYO) | Ahora (este launcher) |
|---|---|---|
| RAM del launcher (PSS) | 17–22 MB | **8,4 MB** |
| CPU con el inicio en pantalla | — | **0 %** |
| Tamaño del APK | — | **118 KB** |
| Velocidad del GPS en el inicio | no | **sí** |
| Cerrar apps abiertas | no | **sí, con la RAM de cada una** |

Medido en la propia radio con `dumpsys meminfo` y `top`: el "antes" el 21/10/2026 y el "ahora" el 22/10/2026.
La **RAM libre del equipo** pasó de ~20 MB a cientos de MB, pero eso vino sobre todo de desactivar apps que no se
usaban (servicios de Google, etc.), no solo del launcher. Por eso no está en la tabla.

## Instalar

Hace falta ADB por Wi‑Fi hacia la radio (puerto 5555). `install.sh` compila el APK y lo instala usando el cliente
ADB en Python de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (`tools/tv.py`), clonado al lado de este repo:

```bash
RADIO_HOST=<ip de la radio> ./install.sh
```

En la radio, al pulsar Home, elegir **Inicio Swift → Siempre**. Algunas funciones (cerrar apps que se resisten,
el chip de la FM) usan el ADB local de la propia radio.

## Volver al launcher de ADAYO

Ajustes → Aplicaciones → Inicio Swift → **Borrar valores predeterminados**, y en el siguiente Home elegir el de
ADAYO. El launcher de fábrica nunca se desactiva.

## Cambiar botones

`app/src/main/java/com/swift/radiohome/MainActivity.java`, tablas `BIG` y `SMALL` (paquete y actividad de cada app).

## Créditos

Hecho por **Rafael WT** con **Claude** (Anthropic). Fuente Outfit (licencia OFL, en `app/src/main/assets/fonts/`).
