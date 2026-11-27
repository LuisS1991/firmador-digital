package com.lnantes.uifirmaelectronica.backend

import com.lnantes.uifirmaelectronica.core.model.CapturedSignature
import com.lnantes.uifirmaelectronica.core.model.DocumentInfo
import com.lnantes.uifirmaelectronica.core.model.ExchangeOperation
import com.lnantes.uifirmaelectronica.core.model.ExchangeResult
import com.lnantes.uifirmaelectronica.core.model.LadoFirma
import com.lnantes.uifirmaelectronica.core.model.MetodoFirma
import com.lnantes.uifirmaelectronica.core.model.Receipt
import com.lnantes.uifirmaelectronica.core.model.ReceiptStatus
import com.lnantes.uifirmaelectronica.core.model.RemoteStation
import com.lnantes.uifirmaelectronica.core.model.SignedDocument
import com.lnantes.uifirmaelectronica.core.model.TabletDevice
import com.lnantes.uifirmaelectronica.core.services.DocumentSigningService
import com.lnantes.uifirmaelectronica.core.services.ReceiptExchangeService
import com.lnantes.uifirmaelectronica.core.services.ReceiptService
import com.lnantes.uifirmaelectronica.core.services.SignatureCaptureService
import com.lnantes.uifirmaelectronica.core.services.SignatureServices
import com.lnantes.uifirmaelectronica.core.services.TabletSyncService
import com.lnantes.uifirmaelectronica.core.util.ReceiptFileName
import com.lnantes.uifirmaelectronica.core.util.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.lnantes.firmadigitalservidor.FirmaReciboController
import org.lnantes.firmadigitalservidor.FirmaSQLiteController
import org.lnantes.firmadigitalservidor.avanzada.FirmadorAvanzadoPkcs11
import org.lnantes.firmadigitalservidor.compartida.ServicioCompartida
import org.lnantes.firmadigitalservidor.pdfs.GeneradorReciboPdf
import org.lnantes.firmadigitalservidor.server.ServidorTcpFirma
import org.lnantes.firmadigitalservidor.serveraos.ConexionAoa
import org.lnantes.firmadigitalservidor.serveraos.ControladorAoa
import org.lnantes.firmadigitalservidor.serveraos.ProtocoloAoa
import org.lnantes.firmadigitalservidor.servicios.ConfiguracionApiFirma
import org.lnantes.firmadigitalservidor.servicios.ServicioApiFirma
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementación JVM real de todos los contratos de la UI.
 *
 *La UI sigue siendo multiplataforma: aquí se concentran PDFBox, PKCS#11,
 * SQLite, SMB, AOA/USB y el servidor TCP. Si mañana hay target nativo, basta
 * con escribir otra implementación de [SignatureServices].
 */
class DesktopSignatureServices(
    val config: BackendConfig = BackendConfig.cargar(),
    private val onLog: (String) -> Unit = {}
) : SignatureServices {

    private val baseDatos = FirmaSQLiteController(config.rutaSqlite)
    private val api = ApiGateway(config, onLog)
    private val generadorRecibo = ReceiptPdfGenerator()
    private val generadorFirmas = GeneradorReciboPdf(config.plantillaPdf, config.sello, onLog)
    private val compartida = ServicioCompartida(config.compartida, onLog = onLog)

    private val configuracionApi = ConfiguracionApiFirma(
        baseUrl = config.apiBaseUrl,
        rutaFirma = config.rutaFirma,
        connectTimeout = Duration.ofSeconds(10),
        requestTimeout = Duration.ofSeconds(20)
    )
    private val servicioApi = ServicioApiFirma(configuracion = configuracionApi, onLog = onLog)
    private val controladorFirma = FirmaReciboController(servicioApi).apply {
        setToken(config.tokenDeepLink)
    }

    private val contadorFirmas = AtomicLong(0)
    private val controladorAoa = ControladorAoa(
        controladorFirma = controladorFirma,
        baseDatos = baseDatos,
        proveedorCodigo = { codigoActual },
        onLog = onLog,
        onFirmaRecibida = { contadorFirmas.incrementAndGet() },
        onEstadoConexion = { estado -> onLog("📱 AOA: $estado") }
    )

    private val servidorTcp = ServidorTcpFirma(
        puerto = config.tcpPuerto,
        buscarFirmaBase64 = { codigo -> baseDatos.obtener(codigo)?.firmaBase64 },
        onLog = onLog
    )

    private val remotos = ConcurrentHashMap<String, ReciboRemoto>()
    /** Último PDF conocido (el que se muestra como documento actual). */
    private val pdfs = ConcurrentHashMap<String, File>()
    /** PDF generado desde la API, sin firmas: entrada estable para la firma común. */
    private val pdfBase = ConcurrentHashMap<String, File>()
    /** PDF con la firma común: entrada estable para la firma avanzada. */
    private val pdfComun = ConcurrentHashMap<String, File>()
    /** Recibo base de cada código (para derivar los nombres de las firmas). */
    private val basePorCodigo = ConcurrentHashMap<String, File>()
    /** Firmas ya aplicadas por PDF de entrada: clave = ruta absoluta, valor = "lado:modalidad". */
    private val firmasAplicadas = ConcurrentHashMap<String, MutableSet<String>>()
    /** PDF temporales generados en este flujo; se borran al finalizar. */
    private val temporalesPorCodigo = ConcurrentHashMap<String, MutableSet<File>>()
    /** El token del deep link sólo se consume en el primer POST de firma común. */
    private val postEnviado = ConcurrentHashMap.newKeySet<String>()
    private val _receipts = MutableStateFlow(cargarRecibosLocales())

    @Volatile
    private var codigoActual: String? = null
    @Volatile
    private var ultimoSelloCapturado: String? = null

    override val tabletSync: TabletSyncService = object : TabletSyncService {
        override suspend fun detectTablet(): Result<TabletDevice> = runCatching {
            withContext(Dispatchers.IO) {
                if (config.usbHabilitado && !controladorAoa.conexion.conectada) {
                    onLog("🔌 Iniciando enlace AOA con el móvil…")
                    controladorAoa.iniciar()
                }
                esperarConexionAoa()
                dispositivoAoa()
            }
        }

        override suspend fun reconectar(): Result<TabletDevice> = runCatching {
            withContext(Dispatchers.IO) {
                onLog("🔄 Reiniciando el enlace AOA…")
                controladorAoa.reiniciar()
                esperarConexionAoa()
                dispositivoAoa()
            }
        }

        override suspend fun synchronize(
            device: TabletDevice,
            onProgress: (String) -> Unit,
        ): Result<Unit> = runCatching {
            withContext(Dispatchers.IO) {
                onProgress("Enviando modo de firma común al móvil…")
                controladorAoa.enviarModoFirma(ProtocoloAoa.MODO_COMUN)
                onProgress("Sincronización AOA lista")
            }
        }
    }

    override val capture: SignatureCaptureService = object : SignatureCaptureService {
        override suspend fun capture(onProgress: (String) -> Unit): Result<CapturedSignature> = runCatching {
            withContext(Dispatchers.IO) {
                onProgress("Esperando los trazos desde el móvil…")
                val inicio = contadorFirmas.get()
                var esperado = 0L
                var payload = controladorAoa.capturador.ultimoPayload
                while ((payload == null || contadorFirmas.get() == inicio) && esperado < 120_000L) {
                    delay(250)
                    esperado += 250
                    payload = controladorAoa.capturador.ultimoPayload
                }
                val recibido = payload ?: error("No llegó ninguna firma del móvil en 120 s")
                val sello = recibido.firmaBase64.take(32)
                ultimoSelloCapturado = sello
                val contenido = recibido.contenidoTexto
                val trazos = contenido.strokes.count { it.isNotEmpty() }
                onProgress("Firma recibida: $trazos trazo(s)")
                CapturedSignature(
                    id = "SIG-${sello.hashCode().toString().take(8)}",
                    signerName = nombreFirmante(codigoActual),
                    capturedAt = formatDateTime(System.currentTimeMillis()),
                    strokeCount = trazos
                )
            }
        }
    }

    override val signing: DocumentSigningService = object : DocumentSigningService {
        override fun currentDocument(): DocumentInfo {
            val codigo = codigoActual ?: config.codigoPorDefecto
            val recibo = _receipts.value.firstOrNull { it.number == codigo }
            return DocumentInfo(
                name = recibo?.fileName ?: "recibo_$codigo.pdf",
                pages = 1,
                sizeKb = pdfs[codigo]?.length()?.div(1024)?.toInt() ?: 0,
                reference = codigo
            )
        }

        override suspend fun sign(
            document: DocumentInfo,
            signature: CapturedSignature,
            lado: LadoFirma,
            onProgress: (String) -> Unit,
        ): Result<SignedDocument> = runCatching {
            withContext(Dispatchers.IO) {
                val codigo = codigoActual ?: config.codigoPorDefecto
                val payload = controladorAoa.capturador.ultimoPayload
                    ?: controladorAoa.capturador.leerPayload()
                    ?: error("No hay trazos capturados para firmar")
                val contenido = payload.contenidoTexto

                val entrada = entradaActual(codigo)
                requireFirmaPendiente(entrada, lado, MetodoFirma.COMUN)
                val base = basePorCodigo[codigo] ?: entrada
                val sufijo = when (lado) {
                    LadoFirma.RECIBE -> "firmado_recibe_comun"
                    LadoFirma.ENTREGA -> "firmado_entrega_comun"
                }
                val salida = nombreDisponible(base.nameWithoutExtension, sufijo)
                prepararSalida(salida)

                onProgress("Estampando los trazos en la columna ${lado.etiqueta}…")
                generadorFirmas.estamparFirmaComun(
                    pdfSalida = salida,
                    contenido = contenido,
                    pdfEntrada = entrada,
                    zona = zona(lado),
                )
                registrarFirma(entrada, salida, lado, MetodoFirma.COMUN, codigo)
                pdfs[codigo] = salida

                onProgress("Guardando la firma en SQLite antes del POST…")
                controladorFirma.setCodigo(codigo)
                controladorFirma.setPayload(payload)
                baseDatos.guardar(codigo, payload)
                controladorFirma.setFirmaGuardada(1)

                // El token del enlace es de un solo uso: se gasta en la firma
                // común del receptor. Las firmas posteriores quedan en el PDF.
                if (postEnviado.add(codigo)) {
                    controladorFirma.setFirmante(signature.signerName)
                    controladorFirma.setLado(lado.name)
                    val preparado = controladorFirma.prepararEnvio()
                        ?: error("No se pudo preparar el POST gzip")
                    onProgress("Enviando JSON comprimido a ${config.apiBaseUrl}${config.rutaFirma}…")
                    val respuesta = controladorFirma.enviarFirmaPostComprimido(preparado.cuerpoComprimido).get()
                    baseDatos.marcarEnvio(codigo, respuesta.ok, respuesta.mensaje)
                    if (!respuesta.ok) error("El servidor rechazó la firma: ${respuesta.mensaje}")
                } else {
                    onProgress("El token ya fue consumido; esta firma se guarda en el PDF local.")
                }

                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(payload.firmaBase64.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                    .take(24)

                _receipts.value = _receipts.value.map { receipt ->
                    if (receipt.number == codigo) {
                        receipt.copy(fileName = salida.name, status = ReceiptStatus.PENDING)
                    } else receipt
                }
                controladorAoa.capturador.descartarUltimo()
                onLog("📄 Firma común de ${lado.etiqueta}: ${salida.absolutePath}")
                SignedDocument(
                    document = document.copy(name = salida.name, reference = codigo),
                    signature = signature,
                    signedAt = formatDateTime(System.currentTimeMillis()),
                    hash = "SHA-256 · $hash"
                )
            }
        }
    }

    override val receipts: ReceiptService = object : ReceiptService {
        override val receipts: StateFlow<List<Receipt>> = _receipts.asStateFlow()

        override suspend fun generateReceipt(onProgress: (String) -> Unit): Result<Receipt> =
            generateReceipt(config.codigoPorDefecto, onProgress)

        override suspend fun generateReceipt(
            code: String?,
            onProgress: (String) -> Unit,
        ): Result<Receipt> = runCatching {
            withContext(Dispatchers.IO) {
                val codigo = normalizarCodigo(code)
                codigoActual = codigo
                controladorFirma.setCodigo(codigo)
                onProgress("Consultando $codigo en la API local…")
                val reciboRemoto = api.obtenerRecibo(codigo)
                remotos[codigo] = reciboRemoto

                val entrega = reciboRemoto.data.subobjeto("entrega")
                val policia = reciboRemoto.data.subobjeto("policia")
                val cedula = ReceiptFileName.cedula(
                    policia.texto("cedula", "ci", "documento", "cedulaIdentidad"),
                    codigo,
                )
                val fecha = ReceiptFileName.fechaCompacta(
                    entrega.texto("fecha"),
                    LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                )
                val secuencia = siguienteSecuencia(cedula, fecha)
                val nombreBase = ReceiptFileName.base(cedula, fecha, secuencia)

                onProgress("Generando el PDF del recibo con los datos de la API ($nombreBase)…")
                val salida = File(config.directorioRecibos, ReceiptFileName.nombre(nombreBase))
                prepararSalida(salida)
                generadorRecibo.generar(reciboRemoto, salida)
                pdfBase[codigo] = salida
                basePorCodigo[codigo] = salida
                pdfComun.remove(codigo)
                pdfs[codigo] = salida
                temporalesPorCodigo[codigo] = mutableSetOf(salida)
                postEnviado.remove(codigo)
                registrarFirmasPrevias(salida)

                val operador = entrega.subobjeto("operador")
                val nuevo = Receipt(
                    id = entrega.texto("id").ifBlank { codigo },
                    number = codigo,
                    concept = entrega.texto("motivo").ifBlank { "Devolución de indumentaria" },
                    amount = "—",
                    signer = policia.texto("nombreCompleto", "nombre").ifBlank { "Funcionario" },
                    entregador = operador.texto("nombre"),
                    issuedAt = entrega.texto("fecha").ifBlank { formatDateTime(System.currentTimeMillis()) },
                    fileName = salida.name,
                    status = ReceiptStatus.PENDING
                )
                _receipts.value = listOf(nuevo) + _receipts.value.filter { it.number != codigo }
                onProgress("Recibo listo: ${salida.name}")
                nuevo
            }
        }

        override suspend fun signWithToken(
            receipt: Receipt,
            lado: LadoFirma,
            firmante: String?,
            onProgress: (String) -> Unit,
        ): Result<Receipt> = runCatching {
            withContext(Dispatchers.IO) {
                val codigo = receipt.number
                val entrada = entradaActual(codigo)
                requireFirmaPendiente(entrada, lado, MetodoFirma.AVANZADA)
                val nombre = firmante?.takeIf { it.isNotBlank() }
                    ?: when (lado) {
                        LadoFirma.ENTREGA -> receipt.entregador.ifBlank { receipt.signer }
                        LadoFirma.RECIBE -> receipt.signer
                    }.ifBlank { "Firmante" }

                val pin = if (config.pinLocal.isNotBlank()) {
                    onProgress("Usando PIN local configurado para el token…")
                    config.pinLocal.toCharArray()
                } else {
                    onProgress("Solicitando PIN al móvil por AOA…")
                    val respuesta = controladorAoa.conexion.solicitarPin(
                        motivo = "Firma avanzada de $lado - recibo $codigo",
                        timeoutMs = 120_000
                    )
                    if (!respuesta.ok) error(respuesta.mensaje)
                    respuesta.pin ?: error("El móvil no devolvió el PIN")
                }

                onProgress("Inicializando token y estampando PAdES de ${lado.etiqueta}…")
                val firmador = FirmadorAvanzadoPkcs11(config.libreriaPkcs11)
                val sufijo = when (lado) {
                    LadoFirma.RECIBE -> "firmado_recibe_avanzada"
                    LadoFirma.ENTREGA -> "firmado_entrega_avanzada"
                }
                val salida = nombreDisponible(entrada.nameWithoutExtension, sufijo)
                prepararSalida(salida)
                generadorFirmas.estamparFirmaAvanzada(
                    pdfSalida = salida,
                    firmador = firmador,
                    pin = pin,
                    pdfEntrada = entrada,
                    zona = zona(lado),
                    nombreFirmante = nombre,
                )
                pin.fill('\u0000')
                registrarFirma(entrada, salida, lado, MetodoFirma.AVANZADA, codigo)
                pdfs[codigo] = salida

                val firmado = receipt.copy(
                    fileName = salida.name,
                    status = ReceiptStatus.SIGNED,
                    signedAt = formatDateTime(System.currentTimeMillis()),
                    tokenSerial = firmador.obtenerNombreComun()?.take(24) ?: "TOKEN"
                )
                _receipts.value = _receipts.value.map { if (it.number == codigo) firmado else it }

                val subida = compartida.guardarRecibo(salida, codigo)
                onLog(if (subida.ok) "☁️ Recibo subido a la compartida" else "⚠️ No se pudo subir: ${subida.mensaje}")
                onProgress("Recibo firmado y archivado: ${salida.name}")
                firmado
            }
        }

        override suspend fun limpiarTemporales(
            codigo: String,
            archivoFinal: String?,
            onProgress: (String) -> Unit,
        ): Result<Unit> = runCatching {
            withContext(Dispatchers.IO) {
                val final = archivoFinal?.let { File(config.directorioRecibos, it).absolutePath }
                val temporales = temporalesPorCodigo.remove(codigo).orEmpty()
                temporales.forEach { archivo ->
                    if (archivo.isFile && archivo.absolutePath != final && archivo.absolutePath != config.plantillaPdf.absolutePath) {
                        if (archivo.delete()) {
                            onProgress("Temporal eliminado: ${archivo.name}")
                        } else {
                            onProgress("No se pudo eliminar el temporal: ${archivo.name}")
                        }
                    }
                }
                pdfBase.remove(codigo)
                pdfComun.remove(codigo)
            }
        }
    }

    override val exchange: ReceiptExchangeService = object : ReceiptExchangeService {
        private val estacion = RemoteStation(
            id = "TIC-SHARE",
            name = config.compartida.share,
            ip = config.compartida.host,
            online = true,
            lastSeen = "configurada"
        )

        override suspend fun discoverStations(onProgress: (String) -> Unit): Result<List<RemoteStation>> = runCatching {
            onProgress("Usando la carpeta compartida configurada…")
            listOf(estacion)
        }

        override suspend fun synchronize(
            station: RemoteStation,
            onProgress: (String) -> Unit,
        ): Result<RemoteStation> = runCatching {
            withContext(Dispatchers.IO) {
                onProgress("Probando conexión con ${station.label}…")
                val prueba = compartida.probarConexion()
                if (!prueba.ok) error(prueba.mensaje)
                onProgress("Compartida disponible: ${prueba.datos}")
                station.copy(lastSeen = "Sincronizada ahora")
            }
        }

        override suspend fun listRemoteReceipts(
            station: RemoteStation,
            onProgress: (String) -> Unit,
        ): Result<List<Receipt>> = runCatching {
            withContext(Dispatchers.IO) {
                onProgress("Listando recibos en la compartida…")
                val resultado = compartida.listar()
                if (!resultado.ok) error(resultado.mensaje)
                resultado.datos.orEmpty()
                    .filter { !it.esDirectorio && it.nombre.endsWith(".pdf", ignoreCase = true) }
                    .mapIndexed { indice, entrada ->
                        Receipt(
                            id = "REM-$indice-${entrada.nombre}",
                            number = entrada.nombre.removeSuffix(".pdf"),
                            concept = "Recibo en compartida",
                            amount = "—",
                            signer = "Estación remota",
                            issuedAt = "",
                            fileName = entrada.nombre,
                            status = ReceiptStatus.SIGNED
                        )
                    }
            }
        }

        override suspend fun exportReceipts(
            receipts: List<Receipt>,
            station: RemoteStation,
            onProgress: (String) -> Unit,
        ): Result<ExchangeResult> = runCatching {
            withContext(Dispatchers.IO) {
                var enviados = 0
                receipts.forEach { recibo ->
                    val archivo = File(config.directorioRecibos, recibo.fileName)
                    if (!archivo.isFile) return@forEach
                    val resultado = compartida.guardar(archivo, archivo.name)
                    if (resultado.ok) enviados++
                    onProgress("${archivo.name}: ${if (resultado.ok) "subido" else resultado.mensaje}")
                }
                ExchangeResult(
                    operation = ExchangeOperation.EXPORT,
                    processed = enviados,
                    detail = "$enviados recibo(s) → ${station.label}",
                    completedAt = formatDateTime(System.currentTimeMillis())
                )
            }
        }

        override suspend fun importReceipts(
            receipts: List<Receipt>,
            station: RemoteStation,
            onProgress: (String) -> Unit,
        ): Result<ExchangeResult> = runCatching {
            withContext(Dispatchers.IO) {
                var importados = 0
                receipts.forEach { recibo ->
                    val destino = File(config.directorioRecibos, recibo.fileName)
                    val resultado = compartida.descargar(recibo.fileName, destino)
                    if (resultado.ok) {
                        importados++
                        _receipts.value = listOf(recibo.copy(status = ReceiptStatus.SIGNED)) + _receipts.value
                    }
                    onProgress("${recibo.fileName}: ${if (resultado.ok) "importado" else resultado.mensaje}")
                }
                ExchangeResult(
                    operation = ExchangeOperation.IMPORT,
                    processed = importados,
                    detail = "$importados recibo(s) ← ${station.label}",
                    completedAt = formatDateTime(System.currentTimeMillis())
                )
            }
        }
    }

    init {
        // Si otra instancia ya usa el puerto o no hay móvil conectado, la app
        // debe seguir abriendo: esos canales se reintentan al firmar.
        if (config.tcpHabilitado) {
            runCatching { servidorTcp.iniciar() }
                .onFailure { error -> onLog("⚠️ TCP ${config.tcpPuerto} no disponible: ${error.message}") }
        }
        if (config.usbHabilitado) {
            runCatching { controladorAoa.iniciar() }
                .onFailure { error -> onLog("⚠️ AOA no disponible: ${error.message}") }
        }
        if (config.tokenDeepLink.isNotBlank()) {
            onLog("🔑 Token del enlace profundo cargado; se enviará en el POST de firma")
        }
        onLog("Backend JVM listo · API ${config.apiBaseUrl} · TCP ${config.tcpPuerto}")
    }

    /** PDF actual del recibo: base, o última firma ya estampada de la cadena. */
    private fun entradaActual(codigo: String): File {
        val nombre = _receipts.value.firstOrNull { it.number == codigo }?.fileName
        return nombre?.let { File(config.directorioRecibos, it).takeIf { archivo -> archivo.isFile } }
            ?: pdfComun[codigo]?.takeIf { it.isFile }
            ?: pdfBase[codigo]?.takeIf { it.isFile }
            ?: basePorCodigo[codigo]?.takeIf { it.isFile }
            ?: File(config.directorioRecibos, "recibo_$codigo.pdf").takeIf { it.isFile }
            ?: config.plantillaPdf
    }

    private fun zona(lado: LadoFirma) = when (lado) {
        LadoFirma.ENTREGA -> org.lnantes.firmadigitalservidor.pdfs.ZonasRecibo.ENTREGA
        LadoFirma.RECIBE -> org.lnantes.firmadigitalservidor.pdfs.ZonasRecibo.RECIBE
    }

    private fun esperarConexionAoa(timeoutMs: Long = 25_000L) {
        var esperado = 0L
        while (config.usbHabilitado && !controladorAoa.conexion.conectada && esperado < timeoutMs) {
            Thread.sleep(250)
            esperado += 250
        }
        if (config.usbHabilitado && !controladorAoa.conexion.conectada) {
            error("El móvil no se conectó por AOA. Conectá el cable, abrí la app de firma y presione «Reintentar conexión AOA».")
        }
    }

    private fun dispositivoAoa() = TabletDevice(
        id = "AOA-${config.tcpPuerto}",
        name = "Móvil de captura (AOA)",
        model = "Android Accessory",
        batteryPercent = 100,
        connectedAt = formatDateTime(System.currentTimeMillis())
    )

    /** Próximo número libre para `recibo_<cedula>_<fecha>_NN.pdf`. */
    private fun siguienteSecuencia(cedula: String, fecha: String): Int {
        val prefijo = Regex.escape(ReceiptFileName.prefijo(cedula, fecha))
        val patron = Regex("^$prefijo(\\d+)(?:_[^.]*)?\\.pdf$", RegexOption.IGNORE_CASE)
        return (config.directorioRecibos.listFiles().orEmpty()
            .mapNotNull { patron.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0) + 1
    }

    /** Busca un nombre que no exista para no sobrescribir firmas anteriores. */
    private fun nombreDisponible(baseSinExtension: String, sufijo: String): File {
        var nombre = ReceiptFileName.nombre(baseSinExtension, sufijo)
        var intento = 2
        while (File(config.directorioRecibos, nombre).isFile) {
            nombre = ReceiptFileName.nombre("${baseSinExtension}_$intento", sufijo)
            intento++
        }
        return File(config.directorioRecibos, nombre)
    }

    /** Recupera del disco las firmas de un recibo generado en una sesión anterior. */
    private fun registrarFirmasPrevias(base: File) {
        val prefijo = base.nameWithoutExtension
        config.directorioRecibos.listFiles { archivo ->
            archivo.isFile && archivo.name.startsWith(prefijo) && archivo.name.endsWith(".pdf", true)
        }.orEmpty().forEach { archivo ->
            val sufijo = archivo.nameWithoutExtension.removePrefix(prefijo).trim('_')
            val firma = when (sufijo) {
                "firmado_comun" -> LadoFirma.RECIBE to MetodoFirma.COMUN
                "firmado_avanzada" -> LadoFirma.ENTREGA to MetodoFirma.AVANZADA
                "firmado_recibe_comun" -> LadoFirma.RECIBE to MetodoFirma.COMUN
                "firmado_recibe_avanzada" -> LadoFirma.RECIBE to MetodoFirma.AVANZADA
                "firmado_entrega_comun" -> LadoFirma.ENTREGA to MetodoFirma.COMUN
                "firmado_entrega_avanzada" -> LadoFirma.ENTREGA to MetodoFirma.AVANZADA
                else -> null
            } ?: return@forEach
            firmasAplicadas.getOrPut(base.absolutePath) { mutableSetOf() } += clave(firma.first, firma.second)
        }
    }

    private fun clave(lado: LadoFirma, metodo: MetodoFirma): String = "$lado:$metodo"

    private fun requireFirmaPendiente(entrada: File, lado: LadoFirma, metodo: MetodoFirma) {
        val aplicadas = firmasAplicadas[entrada.absolutePath].orEmpty()
        check(clave(lado, metodo) !in aplicadas) {
            "Este recibo ya tiene la firma de ${lado.etiqueta} ($metodo); genere otro recibo para firmar de nuevo"
        }
    }

    private fun registrarFirma(
        entrada: File,
        salida: File,
        lado: LadoFirma,
        metodo: MetodoFirma,
        codigo: String,
    ) {
        val heredadas = firmasAplicadas[entrada.absolutePath].orEmpty().toMutableSet()
        heredadas += clave(lado, metodo)
        firmasAplicadas[salida.absolutePath] = heredadas
        temporalesPorCodigo.getOrPut(codigo) { mutableSetOf() } += salida
        if (metodo == MetodoFirma.COMUN) pdfComun[codigo] = salida
    }

    /** Evita el aviso de PDFBox al sobrescribir una salida de una ejecución anterior. */
    private fun prepararSalida(salida: File) {
        salida.parentFile?.mkdirs()
        if (salida.isFile && salida.absolutePath != config.plantillaPdf.absolutePath) {
            salida.delete()
        }
    }

    private fun normalizarCodigo(valor: String?): String {
        val texto = valor?.trim().orEmpty().uppercase()
        val coincide = Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}").find(texto)
        return coincide?.value ?: texto.ifBlank { config.codigoPorDefecto }
    }

    private fun nombreFirmante(codigo: String?): String {
        if (codigo.isNullOrBlank()) return "Firmante"
        return remotos[codigo]?.data?.subobjeto("policia")?.texto("nombreCompleto", "nombre")
            ?.takeIf { it.isNotBlank() } ?: "Firmante"
    }

    private fun cargarRecibosLocales(): List<Receipt> =
        config.directorioRecibos.listFiles { archivo ->
            archivo.isFile && archivo.name.endsWith(".pdf", ignoreCase = true)
        }.orEmpty().sortedByDescending { it.lastModified() }.map { archivo ->
            Receipt(
                id = "LOCAL-${archivo.name}",
                number = archivo.name.removeSuffix(".pdf"),
                concept = "Recibo local",
                amount = "—",
                signer = "Estación local",
                issuedAt = Instant.ofEpochMilli(archivo.lastModified()).toString(),
                fileName = archivo.name,
                status = if (archivo.name.contains("firmado", ignoreCase = true)) {
                    ReceiptStatus.SIGNED
                } else {
                    ReceiptStatus.PENDING
                }
            )
        }
}

/** Fábrica usada por `desktopApp/main.kt`; mantiene la UI libre de detalles JVM. */
fun createDesktopSignatureServices(
    config: BackendConfig = BackendConfig.cargar(),
    onLog: (String) -> Unit = {}
): SignatureServices = DesktopSignatureServices(config, onLog)
