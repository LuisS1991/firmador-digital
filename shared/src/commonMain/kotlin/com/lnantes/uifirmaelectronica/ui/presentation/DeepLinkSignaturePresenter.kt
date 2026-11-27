package com.lnantes.uifirmaelectronica.ui.presentation

import com.lnantes.uifirmaelectronica.core.model.LadoFirma
import com.lnantes.uifirmaelectronica.core.model.LogEntry
import com.lnantes.uifirmaelectronica.core.model.LogLevel
import com.lnantes.uifirmaelectronica.core.model.MetodoFirma
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import com.lnantes.uifirmaelectronica.core.util.currentTimeMillis
import com.lnantes.uifirmaelectronica.core.util.formatClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeepLinkPhase { CARGANDO, LISTO, FIRMANDO_RECEPTOR, FIRMANDO_OPERADOR, FINALIZADO }

data class DeepLinkSignatureUiState(
    val codigo: String,
    val phase: DeepLinkPhase = DeepLinkPhase.CARGANDO,
    val receipt: Receipt? = null,
    /** Solo indica si el enlace trajo token; el valor nunca llega a la UI. */
    val tokenDisponible: Boolean = false,
    val metodoReceptor: MetodoFirma = MetodoFirma.COMUN,
    val metodoOperador: MetodoFirma = MetodoFirma.AVANZADA,
    val archivoBase: String? = null,
    val archivoReceptor: String? = null,
    val archivoOperador: String? = null,
    val firmaReceptorAplicada: Boolean = false,
    val firmaOperadorAplicada: Boolean = false,
    val temporalesEliminados: Boolean = false,
    /** Muestra el botón de reintento cuando falló el enlace AOA. */
    val conexionAoaFallida: Boolean = false,
    val log: List<LogEntry> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** Si se aplicaron ambas firmas, la del operador es el archivo definitivo. */
    val archivoFinal: String?
        get() = archivoOperador ?: archivoReceptor ?: archivoBase

    val puedeFirmar: Boolean
        get() = receipt != null && !busy && phase != DeepLinkPhase.FINALIZADO &&
            (!firmaReceptorAplicada || !firmaOperadorAplicada)
}

sealed interface DeepLinkUiEvent {
    data class SeleccionarMetodoReceptor(val metodo: MetodoFirma) : DeepLinkUiEvent
    data class SeleccionarMetodoOperador(val metodo: MetodoFirma) : DeepLinkUiEvent
    data object Firmar : DeepLinkUiEvent
    data object Regenerar : DeepLinkUiEvent
    data object ReintentarConexion : DeepLinkUiEvent
    data object LimpiarLog : DeepLinkUiEvent
}

private data class PlanFirma(
    val lado: LadoFirma,
    val metodo: MetodoFirma,
    val nombre: String,
)

/**
 * Flujo del enlace profundo `firmaapp://firmar?id=...&token=...`.
 *
 * Ambas partes del recibo deben firmar y cada una elige libremente entre firma
 * común y avanzada. El orden de captura es: primero quien recibe la ropa y
 * después el funcionario que entrega (operador). Las firmas avanzadas se
 * estampan al final para que ningún trazo posterior rompa la integridad PAdES.
 */
class DeepLinkSignaturePresenter(
    private val scope: CoroutineScope,
    private val services: SignatureServices,
    private val codigoInicial: String,
    private val tokenDisponible: Boolean = false,
) {
    private val _state = MutableStateFlow(
        DeepLinkSignatureUiState(
            codigo = codigoInicial,
            tokenDisponible = tokenDisponible,
        )
    )
    val state: StateFlow<DeepLinkSignatureUiState> = _state.asStateFlow()

    private var operationJob: Job? = null
    private var logSequence = 0L

    init {
        generar(codigoInicial)
    }

    fun onEvent(event: DeepLinkUiEvent) {
        when (event) {
            is DeepLinkUiEvent.SeleccionarMetodoReceptor -> _state.update {
                it.copy(metodoReceptor = event.metodo)
            }

            is DeepLinkUiEvent.SeleccionarMetodoOperador -> _state.update {
                it.copy(metodoOperador = event.metodo)
            }

            DeepLinkUiEvent.Firmar -> firmar()
            DeepLinkUiEvent.Regenerar -> generar(_state.value.codigo)
            DeepLinkUiEvent.ReintentarConexion -> reintentarConexion()
            DeepLinkUiEvent.LimpiarLog -> _state.update { it.copy(log = emptyList()) }
        }
    }

    private fun generar(codigo: String) {
        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update {
                it.copy(
                    phase = DeepLinkPhase.CARGANDO,
                    busy = true,
                    error = null,
                    receipt = null,
                    archivoBase = null,
                    archivoReceptor = null,
                    archivoOperador = null,
                    firmaReceptorAplicada = false,
                    firmaOperadorAplicada = false,
                    temporalesEliminados = false,
                    conexionAoaFallida = false,
                )
            }
            log("Obteniendo los datos del recibo $codigo…")
            services.receipts.generateReceipt(codigo) { message -> log(message) }.fold(
                onSuccess = { receipt ->
                    _state.update {
                        it.copy(
                            receipt = receipt,
                            archivoBase = receipt.fileName,
                            phase = DeepLinkPhase.LISTO,
                            busy = false,
                        )
                    }
                    log("Recibo temporal listo: ${receipt.fileName}", LogLevel.SUCCESS)
                    log(
                        "Orden de firma: primero ${LadoFirma.RECIBE.etiqueta} " +
                            "(${_state.value.metodoReceptor.etiqueta}), después " +
                            "${LadoFirma.ENTREGA.etiqueta} (${_state.value.metodoOperador.etiqueta})."
                    )
                    if (!tokenDisponible) {
                        log(
                            "El enlace no trajo token; el envío a Laravel puede ser rechazado.",
                            LogLevel.WARNING,
                        )
                    }
                },
                onFailure = { error -> fail("No se pudo generar el recibo", error) },
            )
        }
    }

    private fun firmar() {
        val actual = _state.value
        if (!actual.puedeFirmar) {
            when {
                actual.phase == DeepLinkPhase.FINALIZADO ->
                    log("Este recibo ya está firmado; genere otro para volver a firmar", LogLevel.WARNING)
                else ->
                    log("Las firmas ya fueron aplicadas a este recibo", LogLevel.WARNING)
            }
            return
        }

        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(busy = true, error = null, conexionAoaFallida = false) }
            try {
                var receipt = _state.value.receipt ?: return@launch
                val planes = listOf(
                    PlanFirma(
                        LadoFirma.RECIBE,
                        _state.value.metodoReceptor,
                        receipt.signer.ifBlank { "Quien recibe" },
                    ),
                    PlanFirma(
                        LadoFirma.ENTREGA,
                        _state.value.metodoOperador,
                        receipt.entregador.ifBlank { "Funcionario que entrega" },
                    ),
                ).filter { plan ->
                    when (plan.lado) {
                        LadoFirma.RECIBE -> !_state.value.firmaReceptorAplicada
                        LadoFirma.ENTREGA -> !_state.value.firmaOperadorAplicada
                    }
                }

                // Los trazos se capturan en orden (recibe → entrega). Las firmas
                // PAdES se dejan para el final: cualquier cambio visual posterior
                // invalidaría la firma digital.
                planes.filter { it.metodo == MetodoFirma.COMUN }.forEach { plan ->
                    fase(plan.lado)
                    val archivo = firmarComun(plan)
                    registrar(plan, archivo)
                    receipt = _state.value.receipt ?: receipt
                }

                val avanzadas = planes.filter { it.metodo == MetodoFirma.AVANZADA }
                if (avanzadas.isNotEmpty()) {
                    log(
                        "Aplicando la(s) firma(s) avanzada(s) al final para conservar la integridad del PDF.",
                        LogLevel.WARNING,
                    )
                }
                avanzadas.forEach { plan ->
                    fase(plan.lado)
                    val firmado = services.receipts.signWithToken(
                        receipt = receipt,
                        onProgress = { message -> log(message) },
                        lado = plan.lado,
                        firmante = plan.nombre,
                    ).getOrThrow()
                    registrar(plan, firmado.fileName)
                    receipt = firmado
                }

                val final = _state.value.archivoFinal
                services.receipts.limpiarTemporales(_state.value.codigo, final) { message -> log(message) }
                    .onFailure { error -> log("No se pudieron borrar los temporales: ${error.message}", LogLevel.WARNING) }
                _state.update {
                    it.copy(
                        phase = DeepLinkPhase.FINALIZADO,
                        busy = false,
                        temporalesEliminados = true,
                    )
                }
                log(
                    "Firma(s) completada(s). Archivo final: ${_state.value.archivoFinal.orEmpty()}",
                    LogLevel.SUCCESS,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(phase = DeepLinkPhase.LISTO) }
                fail("No se pudo completar la firma", error)
            }
        }
    }

    private fun fase(lado: LadoFirma) = _state.update {
        it.copy(
            phase = when (lado) {
                LadoFirma.RECIBE -> DeepLinkPhase.FIRMANDO_RECEPTOR
                LadoFirma.ENTREGA -> DeepLinkPhase.FIRMANDO_OPERADOR
            },
        )
    }

    private fun registrar(plan: PlanFirma, archivo: String) {
        _state.update { estado ->
            val actualizado = estado.receipt?.copy(
                fileName = archivo,
                status = if (plan.metodo == MetodoFirma.AVANZADA) {
                    ReceiptStatus.SIGNED
                } else {
                    estado.receipt.status
                },
            )
            estado.copy(
                receipt = actualizado,
                archivoReceptor = if (plan.lado == LadoFirma.RECIBE) archivo else estado.archivoReceptor,
                archivoOperador = if (plan.lado == LadoFirma.ENTREGA) archivo else estado.archivoOperador,
                firmaReceptorAplicada = if (plan.lado == LadoFirma.RECIBE) true else estado.firmaReceptorAplicada,
                firmaOperadorAplicada = if (plan.lado == LadoFirma.ENTREGA) true else estado.firmaOperadorAplicada,
            )
        }
        log("Firma de ${plan.lado.etiqueta} guardada: $archivo", LogLevel.SUCCESS)
    }

    /** Sincroniza la tablet, espera los trazos y estampa la firma común. */
    private suspend fun firmarComun(plan: PlanFirma): String {
        log("Buscando la tablet para ${plan.lado.etiqueta}…")
        val device = try {
            services.tabletSync.detectTablet().getOrThrow()
        } catch (error: Throwable) {
            _state.update { it.copy(conexionAoaFallida = true) }
            throw error
        }
        services.tabletSync.synchronize(device) { message -> log(message) }.getOrThrow()
        log("Esperando los trazos de ${plan.nombre}…")
        val firma = services.capture.capture { message -> log(message) }.getOrThrow()
        val documento = services.signing.currentDocument()
        val firmado = services.signing.sign(
            document = documento,
            signature = firma.copy(signerName = plan.nombre),
            onProgress = { message -> log(message) },
            lado = plan.lado,
        ).getOrThrow()
        return firmado.document.name
    }

    private fun reintentarConexion() {
        operationJob?.cancel()
        operationJob = scope.launch {
            _state.update { it.copy(busy = true, error = null, conexionAoaFallida = false) }
            log("Reintentando el enlace AOA con el móvil…")
            services.tabletSync.reconectar().fold(
                onSuccess = {
                    _state.update { it.copy(busy = false) }
                    log("Móvil reconectado. Ya puede continuar con la firma.", LogLevel.SUCCESS)
                },
                onFailure = { error ->
                    _state.update { it.copy(conexionAoaFallida = true) }
                    fail("No se pudo reconectar el móvil", error)
                },
            )
        }
    }

    private fun fail(message: String, error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        _state.update { it.copy(busy = false, error = message + detail) }
        log(message + detail, LogLevel.ERROR)
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _state.update { current ->
            current.copy(
                log = (
                    current.log + LogEntry(
                        ++logSequence,
                        formatClock(currentTimeMillis()),
                        message,
                        level,
                    )
                    ).takeLast(80),
            )
        }
    }
}
