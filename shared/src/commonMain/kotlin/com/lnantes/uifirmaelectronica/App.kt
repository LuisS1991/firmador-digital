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
import com.lnantes.uifirmaelectronica.ui.presentation.DeepLinkSignaturePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ExchangePresenter
import com.lnantes.uifirmaelectronica.ui.presentation.ReceiptsPresenter
import com.lnantes.uifirmaelectronica.ui.theme.AppTheme
import com.lnantes.uifirmaelectronica.views.advanced.AdvancedSignatureView
import com.lnantes.uifirmaelectronica.views.common.CommonSignatureView
import com.lnantes.uifirmaelectronica.views.deeplink.DeepLinkSignatureView
import com.lnantes.uifirmaelectronica.views.exchange.ExchangeReceiptsView
import com.lnantes.uifirmaelectronica.views.home.HomeView
import com.lnantes.uifirmaelectronica.views.receipts.SignedReceiptsView

/**
 * Punto de entrada de la UI.
 *
 * El backend real se inyecta desde `desktopApp/main.kt`. El valor por defecto
 * ([DemoSignatureServices]) solo existe para previsualizar la UI sin hardware.
 */
@Composable
fun App(
    services: com.lnantes.uifirmaelectronica.core.services.SignatureServices = DemoSignatureServices(),
    codigoInicial: String = "6B1E-144A-749B",
    modoDeepLink: Boolean = false,
    tokenDisponible: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val navigation = remember { AppNavigation() }
    val destination by navigation.destination.collectAsState()

    val commonPresenter = remember { CommonSignaturePresenter(scope, services) }
    val advancedPresenter = remember { AdvancedSignaturePresenter(scope, services, codigoInicial) }
    val receiptsPresenter = remember { ReceiptsPresenter(scope, services) }
    val exchangePresenter = remember { ExchangePresenter(scope, services) }

    var deepLinkActivo by remember(modoDeepLink) { mutableStateOf(modoDeepLink) }
    val deepLinkPresenter = remember(deepLinkActivo, services, codigoInicial, tokenDisponible) {
        if (deepLinkActivo) {
            DeepLinkSignaturePresenter(scope, services, codigoInicial, tokenDisponible)
        } else {
            null
        }
    }

    var darkTheme by remember { mutableStateOf(false) }

    AppTheme(darkTheme = darkTheme) {
        if (deepLinkActivo && deepLinkPresenter != null) {
            DeepLinkSignatureView(
                presenter = deepLinkPresenter,
                onOpenFullApp = { deepLinkActivo = false },
            )
            return@AppTheme
        }

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
