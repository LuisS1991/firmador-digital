package com.lnantes.uifirmaelectronica.core.navigation

import com.lnantes.uifirmaelectronica.core.model.AppDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Control de navegación en memoria. Se mantiene fuera de la UI para poder
 * sustituirlo por un componente de navegación propio sin tocar las vistas.
 */
class AppNavigation {
    private val history = ArrayDeque<AppDestination>().apply { addLast(AppDestination.HOME) }

    private val _destination = MutableStateFlow(history.last())
    val destination: StateFlow<AppDestination> = _destination.asStateFlow()

    fun navigateTo(destination: AppDestination) {
        if (destination == _destination.value) return
        history.addLast(destination)
        _destination.value = destination
    }

    fun goBack() {
        if (history.size <= 1) return
        history.removeLast()
        _destination.value = history.last()
    }

    fun goHome() = navigateTo(AppDestination.HOME)

    val canGoBack: Boolean get() = history.size > 1
}
