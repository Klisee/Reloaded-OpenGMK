// GameMaker 8 stores "BackgroundMusic" and "Multimedia" sounds as MIDI on Windows, where they
// were played through the system media player. Since they're commonly used for background music,
// they are synthesized to PCM here, which lets them go through the same mixer as everything else.
//
// The whole song used to be rendered up front, the moment the game changed the music. A song is
// minutes long, so that froze the game for seconds every time the player crossed into another
// zone or a battle started. Nothing is rendered ahead of time any more: [MidiSource] synthesizes
// one mixer block at a time, as playback advances, which is a fraction of a millisecond per block.

use rustysynth::{MidiFile, MidiFileSequencer, SoundFont, Synthesizer, SynthesizerSettings};
use serde::{Deserialize, Serialize};
use std::{
    io::Cursor,
    panic::AssertUnwindSafe,
    path::PathBuf,
    sync::{atomic::AtomicU32, Arc},
};
use udon::source::{ChannelCount, Sample, SampleRate, Source};

use super::{make_volume, SoundParams};

const SAMPLE_RATE: u32 = 44100;
const CHANNELS: u16 = 2;
/// Scratch space for one mixer block, so the first callback after a song change does not have to
/// grow the buffers.
const INITIAL_BLOCK: usize = 4096;

/// Where to look for a GM SoundFont. Users can override with the `OPENGMK_SOUNDFONT` env var.
///
/// El orden importa: primero el override del usuario, después las rutas propias de la plataforma
/// y, al final, el SoundFont que viaja con el motor. Antes la lista era solo de Linux, así que en
/// Windows nunca se encontraba ninguno y toda la música MIDI del juego quedaba muda; las rutas de
/// Windows (`gm.dls` sobre todo) estaban ausentes.
fn soundfont_candidates() -> Vec<PathBuf> {
    if let Some(path) = std::env::var_os("OPENGMK_SOUNDFONT") {
        return vec![PathBuf::from(path)]
    }

    let mut candidates = Vec::new();

    #[cfg(target_os = "windows")]
    {
        // `gm.dls` viene con Windows desde XP, pero rustysynth solo entiende SF2 (no DLS), así que no
        // sirve como está. Se listan igualmente por si alguien deja un SF2 junto a ellas.
        if let Some(windir) = std::env::var_os("SystemRoot") {
            let windir = PathBuf::from(windir);
            candidates.push(windir.join("System32\\drivers\\gm.sf2"));
            candidates.push(windir.join("System32\\drivers\\etc\\gm.sf2"));
        }
        if let Some(appdata) = std::env::var_os("APPDATA") {
            candidates.push(PathBuf::from(appdata).join("Reloaded OpenGMK\\soundfont.sf2"));
        }
        if let Some(programdata) = std::env::var_os("ProgramData") {
            candidates.push(PathBuf::from(programdata).join("Reloaded OpenGMK\\soundfont.sf2"));
        }
        // Junto al ejecutable del motor.
        if let Ok(exe) = std::env::current_exe() {
            if let Some(dir) = exe.parent() {
                candidates.push(dir.join("soundfont.sf2"));
            }
        }
    }

    #[cfg(not(target_os = "windows"))]
    {
        candidates.push(PathBuf::from("/usr/share/sounds/sf2/default-GM.sf2"));
        candidates.push(PathBuf::from("/usr/share/sounds/sf2/TimGM6mb.sf2"));
        candidates.push(PathBuf::from("/usr/share/soundfonts/default-GM.sf2"));
        candidates.push(PathBuf::from("/usr/share/soundfonts/FluidR3_GM.sf2"));
        candidates.push(PathBuf::from("/usr/share/sounds/sf2/FluidR3_GM.sf2"));
        // Junto al ejecutable, igual que en Windows.
        if let Ok(exe) = std::env::current_exe() {
            if let Some(dir) = exe.parent() {
                candidates.push(dir.join("soundfont.sf2"));
            }
        }
    }

    candidates
}

/// SoundFont reducido que viaja con el motor y cubre los 128 instrumentos de GM.
///
/// Sin él, un equipo sin SoundFonts del sistema deja toda la música MIDI en silencio, que es lo
/// que pasaba en Windows: el motor solo buscaba rutas de Linux. Se usa como último recurso, así
/// que si el usuario tiene uno mejor (o define `OPENGMK_SOUNDFONT`), ese gana.
///
/// TimGM6mb, GPL-2 (la misma familia que este proyecto). Ver `soundfont/README.md`.
const BUNDLED_SOUNDFONT: &[u8] = include_bytes!("../../soundfont/TimGM6mb.sf2");

fn load_soundfont() -> Option<Vec<u8>> {
    for path in soundfont_candidates() {
        match std::fs::read(&path) {
            Ok(bytes) if !bytes.is_empty() => {
                eprintln!("[midi] soundfont: {}", path.display());
                return Some(bytes)
            },
            Ok(_) => eprintln!("[midi] soundfont vacio: {}", path.display()),
            Err(e) => eprintln!("[midi] no se pudo leer '{}': {}", path.display(), e),
        }
    }
    eprintln!("[midi] sin SoundFont del sistema; se usa el incluido en el motor");
    if BUNDLED_SOUNDFONT.is_empty() {
        None
    } else {
        Some(BUNDLED_SOUNDFONT.to_vec())
    }
}

/// The parsed SoundFont, shared by every synthesizer.
///
/// Parsing it costs real time (the file is several megabytes) and it was being parsed again for
/// every single song, on top of the synthesis. It never changes, so it is parsed once.
fn shared_soundfont() -> Option<Arc<SoundFont>> {
    static SOUNDFONT: std::sync::OnceLock<Option<Arc<SoundFont>>> = std::sync::OnceLock::new();
    SOUNDFONT
        .get_or_init(|| {
            let bytes = load_soundfont()?;
            Some(Arc::new(SoundFont::new(&mut Cursor::new(&bytes)).ok()?))
        })
        .clone()
}

/// Parses a MIDI file. A few of the game's files are RIFF/RMID (a bare MIDI wrapped in a RIFF
/// container), which the parser rejects, so skip to the embedded MThd chunk when there is one.
fn parse(data: &[u8]) -> Result<MidiFile, rustysynth::MidiFileError> {
    let data = match data.windows(4).position(|window| window == b"MThd") {
        Some(offset) => &data[offset..],
        None => data,
    };
    MidiFile::new(&mut Cursor::new(data))
}

/// How long a parsed file lasts, in interleaved samples - the unit the sound handles measure
/// themselves in.
fn length_in_samples(midi: &MidiFile) -> usize {
    // `get_length` unwraps the time of the last event, so a file whose only events are tempo
    // changes panics there. It is worth no more than the silence it would play anyway.
    let seconds = std::panic::catch_unwind(AssertUnwindSafe(|| midi.get_length())).unwrap_or(0.0);
    let frames = (seconds.max(0.0) * f64::from(SAMPLE_RATE)) as usize;
    frames * usize::from(CHANNELS)
}

/// Validates a MIDI file without rendering it. Rendering takes seconds per song, which is far too
/// slow for the `--scan-assets` diagnostic.
pub(crate) fn check(data: &[u8]) -> Result<(), String> {
    parse(data).map(|_| ()).map_err(|e| format!("{:?}", e))
}

/// A MIDI file that is ready to play, and that can be played more than once.
///
/// The parsed file is deliberately not kept: the raw bytes are what get cloned and serialized
/// (savestates carry them like any other sound), and parsing again costs a fraction of a
/// millisecond, while keeping live synthesis state around cannot be serialized at all.
#[derive(Clone, Serialize, Deserialize)]
pub struct MidiHandle {
    data: Box<[u8]>,
    pub(super) params: Arc<SoundParams>,
    pub(super) id: i32,
    pub(super) exclusive: bool,
    length: usize,
}

impl MidiHandle {
    /// Loads a MIDI file, checking that it can be played and measuring how long it lasts.
    pub fn new(data: Box<[u8]>, sound_id: i32, volume: f64, exclusive: bool) -> Option<Self> {
        let length = length_in_samples(&parse(&data).ok()?);
        Some(Self {
            data,
            params: Arc::new(SoundParams { volume: AtomicU32::new(make_volume(volume).to_bits()) }),
            id: sound_id,
            exclusive,
            length,
        })
    }

    pub fn sample_rate(&self) -> u32 {
        SAMPLE_RATE
    }

    pub fn channel_count(&self) -> u16 {
        CHANNELS
    }

    /// The length of the file in interleaved samples, used to work out when playback ends.
    pub fn length(&self) -> usize {
        self.length
    }

    pub fn set_volume(&self, vol: f64) {
        self.params.volume.store(make_volume(vol).to_bits(), std::sync::atomic::Ordering::Release);
    }

    /// A fresh playback of this file. Every play gets its own sequencer, so the same sound can be
    /// played again while an earlier copy is still going.
    pub fn source(&self, looping: bool) -> Option<MidiSource> {
        MidiSource::new(&self.data, looping)
    }
}

/// Synthesizes a MIDI file block by block, as the mixer pulls samples.
pub struct MidiSource {
    sequencer: MidiFileSequencer,
    midi: Arc<MidiFile>,
    /// The file was started with looping on, so it never reaches the end.
    looping: bool,
    /// The sequence finished and the last block has already been handed over; the next call
    /// reports the end so the mixer drops this source.
    finished: bool,
    left: Vec<f32>,
    right: Vec<f32>,
}

impl MidiSource {
    fn new(data: &[u8], looping: bool) -> Option<Self> {
        let soundfont = shared_soundfont()?;
        let settings = SynthesizerSettings::new(i32::try_from(SAMPLE_RATE).ok()?);
        let synthesizer = Synthesizer::new(&soundfont, &settings).ok()?;
        let midi = Arc::new(parse(data).ok()?);

        let mut sequencer = MidiFileSequencer::new(synthesizer);
        sequencer.play(&midi, looping);

        Some(Self {
            sequencer,
            midi,
            looping,
            finished: false,
            left: vec![0.0; INITIAL_BLOCK],
            right: vec![0.0; INITIAL_BLOCK],
        })
    }
}

impl Source for MidiSource {
    fn channel_count(&self) -> ChannelCount {
        ChannelCount::new(CHANNELS).unwrap()
    }

    fn sample_rate(&self) -> SampleRate {
        SampleRate::new(SAMPLE_RATE).unwrap()
    }

    fn write_samples(&mut self, buffer: &mut [Sample]) -> usize {
        let channels = usize::from(CHANNELS);
        let frames = buffer.len() / channels;
        if frames == 0 || self.finished {
            return 0
        }

        self.left.resize(frames, 0.0);
        self.right.resize(frames, 0.0);
        self.left.fill(0.0);
        self.right.fill(0.0);
        self.sequencer.render(&mut self.left, &mut self.right);

        for (frame, (left, right)) in self.left.iter().zip(self.right.iter()).enumerate() {
            buffer[frame * channels] = *left;
            buffer[frame * channels + 1] = *right;
        }
        // The mixer asks for whole frames, but never leave anything it did not ask for unwritten.
        for sample in &mut buffer[frames * channels..] {
            *sample = 0.0;
        }

        if !self.looping && self.sequencer.end_of_sequence() {
            // This block still holds the tail of the song, so it is handed over and the end is
            // reported on the following call, once it has been heard.
            self.finished = true;
        }

        buffer.len()
    }

    fn reset(&mut self) {
        self.sequencer.play(&self.midi, self.looping);
        self.finished = false;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// El SoundFont incluido tiene que parsear: si `SoundFont::new` falla, el motor seguiría
    /// arrancando pero toda la música MIDI quedaría muda sin ningún error visible.
    #[test]
    fn bundled_soundfont_parses() {
        let bytes = BUNDLED_SOUNDFONT;
        assert!(!bytes.is_empty(), "el SoundFont incluido está vacío");
        SoundFont::new(&mut Cursor::new(bytes)).expect("el SoundFont incluido no parsea");
    }

    /// Un SoundFont puede cargar y aun así no producir sonido si las muestras están truncadas.
    /// Aquí se toca una nota y se comprueba que salen muestras distintas de cero.
    #[test]
    fn soundfont_produces_audio() {
        let bytes = load_soundfont().expect("no se encontró ningún SoundFont");
        let font = SoundFont::new(&mut Cursor::new(&bytes)).expect("el SoundFont no parsea");

        let settings = SynthesizerSettings::new(SAMPLE_RATE as i32);
        let font = Arc::new(font);
        let mut synth = Synthesizer::new(&font, &settings).expect("no se pudo crear el sintetizador");

        // (canal, nota, velocidad): Do central, con el preset de piano que asigna el canal 0.
        synth.note_on(0, 60, 100);

        let mut left = vec![0.0f32; 1024];
        let mut right = vec![0.0f32; 1024];
        synth.render(&mut left, &mut right);

        let peak = left
            .iter()
            .chain(right.iter())
            .fold(0.0f32, |acc, s| acc.max(s.abs()));
        assert!(
            peak > 0.001,
            "el sintetizador devolvió silencio (pico {peak}); el SoundFont no sirve para MIDI"
        );
    }
}
