package com.lnantes.uifirmaelectronica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lnantes.uifirmaelectronica.core.navigation.AppNavigation
import com.lnantes.uifirmaelectronica.data.demo.DemoSignatureServices
import com.lnantes.uifirmaelectronica.ui.components.AppScaffold
import com.lnantes.uifirmaelectronica.ui.presentation.AdvancedSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.CommonSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ExchangePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ReceiptsPresenter
import com.lnantes.uifirmaelectronica.ui.theme.AppTheme
import com.lnantes.uifirmaelectronica.views.advanced.AdvancedSignatureView
import com.lnantes.uifirmaelectronica.views.common.CommonSignatureView
import com.lnantes.uifirmaelectronica.views.exchange.ExchangeReceiptsView
import com.lnantes.uifirmaelectronica.views.home.HomeView
import com.lnantes.uifirmaelectronica.views.receipts.SignedReceiptsView

/**
 * Punto de entrada de la UI.
 *
 * Para conectar el backend real, sustituya [DemoSignatureServices] por su
 * implementación de [com.lnantes.uifirmaelectronica.core.services.SignatureServices].
 */
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val services = remember { DemoSignatureServices() }
    val navigation = remember { AppNavigation() }
    val destination by navigation.destination.collectAsState()

    val commonPresenter = remember { CommonSignaturePresenter(scope, services) }
    val advancedPresenter = remember { AdvancedSignaturePresenter(scope, services) }
    val receiptsPresenter = remember { ReceiptsPresenter(scope, services) }
    val exchangePresenter = remember { ExchangePresenter(scope, services) }

    var darkTheme by remember { mutableStateOf(false) }

    AppTheme(darkTheme = darkTheme) {
        AppScaffold(
            current = destination,
            darkTheme = darkTheme,
            onToggleTheme = { darkTheme = !darkTheme },
            onNavigate = { navigation.navigateTo(it) },
        ) {
            when (destination) {
                com.lnantes.uifirmaelectronica.core.model.AppDestination.HOME ->
                    HomeView(onNavigate = { navigation.navigateTo(it) })
                com.lnantes.uifirmaelectronica.core.model.AppDestination.COMMON ->
                    CommonSignatureView(presenter = commonPresenter)
                com.lnantes.uifirmaelectronica.core.model.AppDestination.ADVANCED ->
                    AdvancedSignatureView(
                        presenter = advancedPresenter,
                        onNavigate = { navigation.navigateTo(it) },
                    )
                com.lnantes.uifirmaelectronica.core.model.AppDestination.RECEIPTS ->
                    SignedReceiptsView(
                        presenter = receiptsPresenter,
                        onNavigate = { navigation.navigateTo(it) },
                    )
                com.lnantes.uifirmaelectronica.core.model.AppDestination.EXCHANGE ->
                    ExchangeReceiptsView(presenter = exchangePresenter)
            }
        }
    }
}
