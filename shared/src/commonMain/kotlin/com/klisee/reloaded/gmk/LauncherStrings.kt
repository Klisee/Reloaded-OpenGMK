package com.klisee.reloaded.gmk

import androidx.compose.runtime.staticCompositionLocalOf

expect fun systemLanguage(): String

/**
 * Textos de la interfaz del launcher. Los ajustes guardan `auto`, `es` o `en`;
 * `auto` sigue el idioma del sistema.
 */
data class LauncherStrings(
    val welcomeTo: String,
    val brandTitle: String,
    val welcomeSubtitle: String,
    val continueLabel: String,
    val nextStep: String,
    val stepOf: (Int, Int) -> String,
    val back: String,
    val skip: String,
    val language: String,
    val lightTheme: String,
    val darkTheme: String,
    val installNeeded: String,
    val installTitle: String,
    val installBody: String,
    val gameReady: String,
    val noGame: String,
    val importToContinue: String,
    val downloading: String,
    val importing: String,
    val download: String,
    val downloadFromBlog: String,
    val importZip: String,
    val almostReady: String,
    val howToPlay: String,
    val keepScreenOn: String,
    val keepScreenOnSubtitle: String,
    val touchControlsNote: String,
    val changeLater: String,
    val startPlaying: String,
    val installToContinue: String,
    val allReady: String,
    val canChangeLater: String,
    val languageTitle: String,
    val languageAuto: String,
    val languageAutoSubtitle: String,
    val languageSpanish: String,
    val languageSpanishSubtitle: String,
    val languageEnglish: String,
    val languageEnglishSubtitle: String,
    val close: String,
)

private val Spanish = LauncherStrings(
    welcomeTo = "BIENVENIDO A",
    brandTitle = "RELOADED OPENGMK",
    welcomeSubtitle = "Launcher no oficial para Pokémon Reloaded hecho por Klisee para la comunidad.",
    continueLabel = "Continuar",
    nextStep = "Continuar",
    stepOf = { step, total -> "Paso $step de $total" },
    back = "Atrás",
    skip = "Saltar",
    language = "Idioma",
    lightTheme = "Tema claro",
    darkTheme = "Tema oscuro",
    installNeeded = "Paso clave",
    installTitle = "Necesitas el juego",
    installBody = "Este launcher no incluye ningún archivo del juego. Descárgalo del blog oficial o importa el zip que ya tengas.",
    gameReady = "Juego listo",
    noGame = "Sin juego instalado",
    importToContinue = "Importa el zip para continuar",
    downloading = "Importando…",
    importing = "Importando…",
    download = "Descargar",
    downloadFromBlog = "Descargar del blog",
    importZip = "Importar un zip",
    almostReady = "Casi listo",
    howToPlay = "Cómo quieres jugar",
    keepScreenOn = "Mantener la pantalla encendida",
    keepScreenOnSubtitle = "Evita que el móvil se bloquee durante una batalla larga",
    touchControlsNote = "Los controles táctiles aparecen solos: joystick y botones durante el juego, y ninguno en los menús.",
    changeLater = "Puedes cambiar todo esto cuando quieras desde Ajustes.",
    startPlaying = "Empezar a jugar",
    installToContinue = "Instala el juego para poder continuar.",
    allReady = "Todo listo. ¡Disfruta de Pokémon Reloaded!",
    canChangeLater = "Puedes cambiar cualquier ajuste más tarde.",
    languageTitle = "Idioma",
    languageAuto = "Automático",
    languageAutoSubtitle = "Según el idioma del dispositivo",
    languageSpanish = "Español",
    languageSpanishSubtitle = "Interfaz y texto del juego en español",
    languageEnglish = "English",
    languageEnglishSubtitle = "Interface and game text in English",
    close = "Cerrar",
)

private val English = LauncherStrings(
    welcomeTo = "WELCOME TO",
    brandTitle = "RELOADED OPENGMK",
    welcomeSubtitle = "Unofficial launcher for Pokémon Reloaded, made by Klisee for the community.",
    continueLabel = "Continue",
    nextStep = "Continue",
    stepOf = { step, total -> "Step $step of $total" },
    back = "Back",
    skip = "Skip",
    language = "Language",
    lightTheme = "Light theme",
    darkTheme = "Dark theme",
    installNeeded = "Key step",
    installTitle = "You need the game",
    installBody = "This launcher does not include any game files. Download it from the official blog or import a zip you already have.",
    gameReady = "Game ready",
    noGame = "No game installed",
    importToContinue = "Import the zip to continue",
    downloading = "Importing…",
    importing = "Importing…",
    download = "Download",
    downloadFromBlog = "Download from the blog",
    importZip = "Import a zip",
    almostReady = "Almost done",
    howToPlay = "How do you want to play",
    keepScreenOn = "Keep the screen on",
    keepScreenOnSubtitle = "Stops your phone from locking during a long battle",
    touchControlsNote = "Touch controls appear on their own: joystick and buttons while playing, none in menus.",
    changeLater = "You can change all of this later in Settings.",
    startPlaying = "Start playing",
    installToContinue = "Install the game to continue.",
    allReady = "All set. Enjoy Pokémon Reloaded!",
    canChangeLater = "You can change any setting later.",
    languageTitle = "Language",
    languageAuto = "Automatic",
    languageAutoSubtitle = "Follow the device language",
    languageSpanish = "Español",
    languageSpanishSubtitle = "Interfaz y texto del juego en español",
    languageEnglish = "English",
    languageEnglishSubtitle = "Interface and game text in English",
    close = "Close",
)

fun launcherStrings(language: String): LauncherStrings = when (resolveLauncherLanguage(language)) {
    "en" -> English
    else -> Spanish
}

fun resolveLauncherLanguage(language: String): String = when (language) {
    "es", "en" -> language
    else -> systemLanguage().lowercase().take(2).let { if (it == "en") "en" else "es" }
}

val LocalLauncherStrings = staticCompositionLocalOf { Spanish }
