# SoundFont de MIDI

`TimGM6mb.sf2` va embebido en `gm8emulator.exe` con `include_bytes!`, así que la
música MIDI del juego suena sin depender de nada instalado en la máquina.

## Qué es

Banco General MIDI de 6 MB con **136 presets** (los 128 de GM más variaciones de
percusión). Es el que Debian usa por defecto, y el mismo que trae MuseScore.

- Origen: <https://github.com/musescore/musescore-old/commit/90c33ef9d87b3f5ff92efd3b07d89eb455fb1fef>
- Copyright: 2004 Tim Brechbill, 2010 David Bolton
- Licencia: **GPL-2**

## Por qué este y no otro

La licencia importa: Reloaded OpenGMK es `GPL-2.0-only` y el SoundFont viaja
dentro del binario, así que tiene que ser compatible. TimGM6mb es GPL-2, la
misma familia.

Se probaron otros dos antes:

- **Vintage Dreams Waves v2** (repo de FluidSynth): también parsea y es más
  liviano, pero su licencia no está clara para redistribución.
- Cualquier `.sf2` comercial: incompatible con la GPL del proyecto.

## Comprobarlo

```powershell
.\tools\check-soundfont.ps1
```

Revisa la estructura del SF2, cuenta los presets y confirma que el motor lo
lleva dentro.

## Cambiarlo sin recompilar

El motor busca primero un SoundFont propio del sistema. Basta con dejar el
archivo en:

```
%APPDATA%\Reloaded OpenGMK\soundfont.sf2
```

o junto al motor, en `%APPDATA%\Reloaded OpenGMK\engine\soundfont.sf2`. El
override más explícito es la variable de entorno `OPENGMK_SOUNDFONT`.

Si el archivo elegido no parsea, el motor lo dice en el log y sigue con el
incluido, así que una prueba fallida no deja el juego mudo.
