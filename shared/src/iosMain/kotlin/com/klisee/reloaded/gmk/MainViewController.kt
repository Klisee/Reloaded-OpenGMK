package com.klisee.reloaded.gmk

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val repo = IosGameStore()
    val settings = IosSettings()
    val scope = rememberCoroutineScope()
    App(
        repo = repo,
        settings = settings,
        onPickZip = { callback ->
            scope.launch {
                val file = runCatching {
                    FileKit.openFilePicker(type = FileKitType.File("zip"))
                }.getOrNull()
                callback(file?.let { IosPickedFile(it.path) })
            }
        },
        onPickImage = { callback ->
            scope.launch {
                val file = runCatching {
                    FileKit.openFilePicker(type = FileKitType.File("png", "jpg", "jpeg"))
                }.getOrNull()
                callback(file?.let { IosPickedFile(it.path) })
            }
        },
        onSaveFile = { _, _ -> },
        onLaunch = {},
        onOpenUrl = {},
        appVersion = "0.5.0-ios",
        supportedAbis = listOf("arm64"),
        connectedPads = emptyList(),
        refreshRate = 60f,
    )
}
