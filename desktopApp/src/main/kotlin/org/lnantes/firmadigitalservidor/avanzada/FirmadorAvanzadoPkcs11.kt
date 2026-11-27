package org.lnantes.firmadigitalservidor.avanzada

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.InputStream
import java.security.AuthProvider
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * Acceso al documento de identidad / token criptográfico mediante PKCS#11.
 *
 * Esta clase es la única responsable de hablar con el dispositivo: obtiene la
 * clave privada (que nunca sale del chip) y la cadena de certificados, y
 * devuelve el bloque CMS/PKCS#7 que PDFBox incrusta en el PDF.
 *
 * Si no se le indica una ruta, se toma automáticamente de `~/apiaApplet.cfg`
 * (o de `-Dpkcs11.library` / `PKCS11_LIBRARY`).
 */
class FirmadorAvanzadoPkcs11(private val rutaLibreriaPkcs11: String = "") : SignatureInterface {
    private var pkcs11Provider: Provider? = null
    private var clavePrivada: PrivateKey? = null
    private var cadenaCertificados: List<X509Certificate>? = null

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun estaInicializado(): Boolean = clavePrivada != null && !cadenaCertificados.isNullOrEmpty()

    /**
     * Inicializa la sesión PKCS#11 con el lector/token.
     * Si pinUsuario es null, el driver/middleware suele solicitar el PIN de forma nativa.
     */
    fun inicializarDispositivo(pinUsuario: CharArray? = null, slotIndex: Int = 0) {
        // Descarta cualquier sesión anterior antes de abrir una nueva.
        liberarProveedor()

        val libreria = rutaLibreriaPkcs11.takeIf { it.isNotBlank() }
            ?: ConfiguracionPkcs11.descubrirRutaLibreria()
            ?: throw IllegalStateException(
                "No se pudo determinar la librería PKCS#11: no se indicó una ruta " +
                    "y no se encontró ~/apiaApplet.cfg"
            )

        try {
            val nombreInstancia = "Firmador_${System.nanoTime()}"
            val configInline = """
                --name=$nombreInstancia
                library=$libreria
                slotListIndex=$slotIndex
            """.trimIndent()

            val baseProvider: Provider = Security.getProvider("SunPKCS11")
                ?: throw IllegalStateException("Proveedor SunPKCS11 no disponible en esta JVM")

            val configuredProvider = baseProvider.configure(configInline)
            Security.addProvider(configuredProvider)
            pkcs11Provider = configuredProvider

            val keyStore = KeyStore.getInstance("PKCS11", configuredProvider)
            keyStore.load(null, pinUsuario)

            // Busca una entrada de clave privada con certificado válido para firma
            val alias = keyStore.aliases().toList().firstOrNull { aliasActual ->
                keyStore.isKeyEntry(aliasActual) &&
                        (keyStore.getCertificate(aliasActual) as? X509Certificate)?.let { cert ->
                            val keyUsage = cert.keyUsage
                            // bit 0 = digitalSignature, bit 1 = nonRepudiation (contentCommitment)
                            keyUsage == null || keyUsage[0] || keyUsage[1]
                        } == true
            } ?: throw IllegalStateException("No se encontró un certificado de firma habilitado en el dispositivo")

            clavePrivada = keyStore.getKey(alias, pinUsuario) as? PrivateKey
                ?: throw IllegalStateException("No se pudo obtener la clave privada para el alias: $alias")

            val chain = keyStore.getCertificateChain(alias)
                ?.filterIsInstance<X509Certificate>()
                .orEmpty()

            if (chain.isEmpty()) {
                throw IllegalStateException("La cadena de certificados está vacía para el alias: $alias")
            }
            cadenaCertificados = chain
        } finally {
            // Limpia el PIN en memoria lo antes posible
            pinUsuario?.fill('\u0000')
        }
    }

    /**
     * Callback invocado por PDFBox con los bytes exactos del ByteRange a firmar.
     * Devuelve el bloque CMS/PKCS#7 firmado para incrustar en /Contents.
     */
    override fun sign(content: InputStream): ByteArray {
        check(estaInicializado()) {
            "Debe invocar inicializarDispositivo() antes de firmar"
        }
        val provider = pkcs11Provider
            ?: throw IllegalStateException("El proveedor PKCS#11 no está disponible")
        val key = clavePrivada
            ?: throw IllegalStateException("La clave privada del dispositivo no está disponible")
        val chain = cadenaCertificados
            ?: throw IllegalStateException("La cadena de certificados no está disponible")

        val bytesAFirmar = content.readBytes()
        val certFirmante = chain.first()

        val algoritmoFirma = resolverAlgoritmoFirma(key, certFirmante)
        // El digest se calcula localmente (no en el token); usar el provider
        // por defecto evita fallos en binarios ProGuard donde el provider BC
        // puede no resolver algoritmos por nombre.
        val digestProvider = runCatching {
            JcaDigestCalculatorProviderBuilder().build()
        }.getOrElse {
            JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build()
        }

        val contentSigner = JcaContentSignerBuilder(algoritmoFirma)
            .setProvider(provider) // Usa directamente la instancia PKCS#11 configurada
            .build(key)

        val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(digestProvider)
            .build(contentSigner, certFirmante)

        val cmsGenerator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(signerInfoGenerator)
            addCertificates(JcaCertStore(chain))
        }

        val datosFirmables = CMSProcessableByteArray(bytesAFirmar)
        // encapsulate = false porque PDFBox ya guarda el documento en el ByteRange
        val cmsFirmado = cmsGenerator.generate(datosFirmables, false)

        return cmsFirmado.encoded
    }

    /**
     * Verifica vigencia y (opcionalmente) revocación OCSP antes de firmar.
     * Debe llamarse después de [inicializarDispositivo].
     */
    fun validarCertificado(verificarRevocacion: Boolean = false): ValidadorCertificado.Resultado {
        check(estaInicializado()) { "El dispositivo PKCS#11 no está inicializado" }
        val chain = cadenaCertificados!!
        return ValidadorCertificado.validar(
            cert = chain.first(),
            emisor = chain.getOrNull(1),
            verificarRevocacion = verificarRevocacion
        )
    }

    /** Cadena de certificados leída del token (el primero es el certificado del firmante). */
    fun obtenerCadenaCertificados(): List<X509Certificate> {
        check(estaInicializado()) { "El dispositivo PKCS#11 no está inicializado" }
        return cadenaCertificados!!.toList()
    }

    /** Nombre común (CN) del titular del certificado, para el sello visible y /Name. */
    fun obtenerNombreComun(): String? {
        val chain = cadenaCertificados ?: return null
        if (chain.isEmpty()) return null
        val dn = chain.first().subjectX500Principal.name
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: dn
    }

    /**
     * Remueve el proveedor PKCS#11 registrado, descarta credenciales y cierra
     * la sesión con el token. Hacerlo antes de crear otro proveedor evita que
     * un segundo intento de firma (sin reiniciar la app) falle por sesión
     * abierta o proveedor duplicado.
     */
    fun liberarProveedor() {
        pkcs11Provider?.let { provider ->
            runCatching {
                // SunPKCS11 implementa AuthProvider: cerrar la sesión logueada
                // antes de sacar el provider de Security.
                (provider as? AuthProvider)?.logout()
            }
            Security.removeProvider(provider.name)
        }
        pkcs11Provider = null
        clavePrivada = null
        cadenaCertificados = null
    }

    private fun resolverAlgoritmoFirma(privateKey: PrivateKey, cert: X509Certificate): String {
        val keyAlgorithm = (privateKey.algorithm ?: cert.publicKey.algorithm)
            .uppercase(Locale.ROOT)
        return when {
            keyAlgorithm.contains("EC") -> "SHA256withECDSA"
            keyAlgorithm.contains("RSA") -> "SHA256withRSA"
            else -> throw IllegalArgumentException("Algoritmo de clave no soportado directamente: $keyAlgorithm")
        }
    }
}
