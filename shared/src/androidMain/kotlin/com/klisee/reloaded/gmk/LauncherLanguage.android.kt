package com.klisee.reloaded.gmk

import java.util.Locale

actual fun systemLanguage(): String = Locale.getDefault().language
