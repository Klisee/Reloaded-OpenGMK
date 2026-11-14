package com.klisee.reloaded.gmk

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme
