# Respaldo de la base de fuentes de ADAYO

`params.db.original` es la base `/data/data/com.adayo.midware.service/databases/params.db` tal como estaba
antes de tocarla (22/10/2026). Tiene una sola tabla, `SourceBean`, con la última fuente de audio que la radio
retoma al dar contacto. El valor original era `FM`. No tiene datos personales.

El launcher la cambia a `BTAUDIO` (Bluetooth) con el chip rojo, para que la radio arranque en silencio.
**No usar `NULL`**: sin fuente, el sistema no tiene sobre qué mezclar el audio de las apps y RadioTube no suena.

Para volver al original (con root, y después apagar y encender el auto):

```bash
adb push params.db.original /data/local/tmp/params.db
adb shell "cp /data/local/tmp/params.db /data/data/com.adayo.midware.service/databases/params.db"
```
