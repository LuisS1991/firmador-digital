package com.lnantes.uifirmaelectronica

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.lnantes.uifirmaelectronica.backend.createDesktopSignatureServices
import java.net.URLDecoder

private const val CODIGO_DEFECTO = "6B1E-144A-749B"
private val CODIGO_REGEX = Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")

data class ParametrosDeepLink(
    val codigo: String?,
    val token: String?,
)

/** Lee `?nombre=valor` o `&nombre=valor` sin depender del esquema del enlace. */
private fun valorParametro(argumento: String, nombre: String): String? {
    val coincidencia = Regex("""(?:^|[?&])${Regex.escape(nombre)}=([^&#]+)""")
        .find(argumento) ?: return null
    return runCatching { URLDecoder.decode(coincidencia.groupValues[1], "UTF-8") }
        .getOrNull()
}

/** Acepta también `--codigo=...` y `--token=...` para pruebas desde terminal. */
private fun valorOpcion(argumento: String, nombre: String): String? {
    val prefijo = "--$nombre="
    return argumento.takeIf { it.startsWith(prefijo) }?.removePrefix(prefijo)
}

/**
 * Deep link real de Laravel:
 *   firmaapp://firmar?id=D896-C2E5-27D7&token=<token de 3 minutos>
 *
 * Se mantienen `codigo=` y el código suelto por compatibilidad con versiones
 * anteriores del escritorio. El token NUNCA se muestra en la UI ni en logs.
 */
fun parametrosDeepLink(args: Array<String>): ParametrosDeepLink {
    val codigo = args.firstNotNullOfOrNull { argumento ->
        valorParametro(argumento, "id")
            ?: valorParametro(argumento, "codigo")
            ?: valorOpcion(argumento, "codigo")
            ?: argumento.takeIf { CODIGO_REGEX.matches(it) }
    }
        ?.trim()
        ?.uppercase()
        ?.takeIf { CODIGO_REGEX.matches(it) }

    val token = args.firstNotNullOfOrNull { argumento ->
        valorParametro(argumento, "token") ?: valorOpcion(argumento, "token")
    }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    return ParametrosDeepLink(codigo = codigo, token = token)
}

fun main(args: Array<String>) = application {
    val enlace = parametrosDeepLink(args)
    enlace.codigo?.let { System.setProperty("firmador.codigo", it) }
    enlace.token?.let { System.setProperty("firmador.token", it) }

    val modoDeepLink = enlace.codigo != null
    val services = remember { createDesktopSignatureServices() }
    Window(
        onCloseRequest = ::exitApplication,
        title = if (modoDeepLink) {
            "Firma desde enlace · ${enlace.codigo}"
        } else {
            "Firma Electrónica · Vestuarios"
        },
        state = WindowState(width = 1360.dp, height = 860.dp),
    ) {
        App(
            services = services,
            codigoInicial = enlace.codigo ?: CODIGO_DEFECTO,
            modoDeepLink = modoDeepLink,
            tokenDisponible = enlace.token != null,
        )
    }
}
