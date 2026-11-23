package com.lnantes.uifirmaelectronica

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Firma Electrónica · Consola de escritorio",
        state = WindowState(width = 1360.dp, height = 860.dp),
    ) {
        App()
    }
}
