package com.klisee.reloaded.gmk

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun touchAsset(name: String): ImageBitmap?

@Composable
expect fun TouchEditorOrientation()

@Composable
expect fun TouchEditorScreen(settings: SettingsStore, onBack: () -> Unit)
