# rustysynth parcheado (vendor/rustysynth)

Copia de `rustysynth` 1.3.6 (licencia MIT) con **un solo cambio**, en
`src/soundfont.rs`.

## El problema

La versión de crates.io rechaza SoundFonts válidos con `SanityCheckFailed`.
Afecta a TimGM6mb (el estándar de Debian) y al que distribuye FluidSynth, es
decir, prácticamente a cualquier banco General MIDI real.

El chequeo comparaba los límites de cada región con

```rust
end as usize >= self.wave_data.len()
```

pero en el formato SF2 `end` es el final **exclusivo** de la muestra. Un sonido
que llega hasta la última muestra del banco da exactamente
`wave_data.len()` y se marcaba como corrupto.

Consecuencia en Reloaded OpenGMK: el motor arranca, pero toda la música MIDI
queda muda. El error no se ve en pantalla; solo aparece en el log como
`SanityCheckFailed` si se instrumenta el parseo.

Está reportado aguas arriba:

- <https://github.com/sinshu/rustysynth/issues/22>
- <https://github.com/sinshu/rustysynth/issues/33>
- <https://github.com/sinshu/rustysynth/pull/51>

## El cambio

Se acepta `end` hasta el tamaño del banco (`>` en vez de `>=`) y se valida que
los rangos no queden invertidos ni negativos:

```rust
end as usize > len
end_loop as usize > len
```

Todo lo demás sigue igual. Los tests de `gm8emulator` cubren el caso:
`audio::midi::tests` comprueba que el SoundFont incluido parsea y que al tocar
una nota salen muestras distintas de cero.

## Cómo mantenerlo

Cuando `rustysynth` publique una versión con el arreglo, se puede volver a la
dependencia de crates.io: hay que cambiar en `gm8emulator/Cargo.toml`

```toml
rustysynth = { path = "../../../vendor/rustysynth" }
```

por la versión nueva, y borrar esta carpeta. Antes de hacerlo, correr los tests
de audio para confirmar que el SoundFont incluido sigue parseando.
