package com.klisee.reloaded.gmk

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.klisee.reloaded.gmk.resources.Res
import com.klisee.reloaded.gmk.resources.inter_black
import org.jetbrains.compose.resources.Font

/**
 * Tipografía de titulares: Inter Black (SIL Open Font License 1.1).
 * Es plana, ancha y con mucho peso, pensada para títulos grandes en mayúsculas.
 */
@Composable
fun reloadedDisplayFont(): FontFamily = FontFamily(Font(Res.font.inter_black, FontWeight.Black))
