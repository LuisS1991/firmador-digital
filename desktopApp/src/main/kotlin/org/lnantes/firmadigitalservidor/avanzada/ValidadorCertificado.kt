package org.lnantes.firmadigitalservidor.avanzada

import org.bouncycastle.asn1.ASN1IA5String
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.AuthorityInformationAccess
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.cert.ocsp.BasicOCSPResp
import org.bouncycastle.cert.ocsp.CertificateID
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReqBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Verificación previa a la firma, según los requisitos 5 y 6 de la guía UCE:
 * el certificado debe estar dentro de su período de validez y no revocado.
 *
 * Un sello de tiempo/LTV NO habilita firmar con un certificado vencido: solo
 * preserva la validez de firmas realizadas mientras el certificado estaba vigente.
 */
object ValidadorCertificado {

    enum class EstadoRevocacion {
        NO_VERIFICADA, SIN_OCSP, VIGENTE, REVOCADA, DESCONOCIDA, ERROR
    }

    data class Resultado(
        val vigente: Boolean,
        val motivo: String?,
        val notBefore: Date,
        val notAfter: Date,
        val diasRestantes: Long,
        val revocacion: EstadoRevocacion,
        val detalleRevocacion: String? = null,
        val resumen: String = ""
    )

    /** Validación de vigencia + revocación (OCSP) para el certificado del firmante. */
    @JvmStatic
    fun validar(
        cert: X509Certificate,
        emisor: X509Certificate? = null,
        fecha: Date = Date(),
        verificarRevocacion: Boolean = false
    ): Resultado {
        val notBefore = cert.notBefore
        val notAfter = cert.notAfter

        val motivo = when {
            fecha.before(notBefore) ->
                "El certificado aún no es válido (comienza el ${formatear(notBefore)})"
            fecha.after(notAfter) ->
                "El certificado está vencido (venció el ${formatear(notAfter)})"
            else -> null
        }

        val diasRestantes = Math.floorDiv(notAfter.time - fecha.time, 86_400_000L)

        var estadoRevocacion = EstadoRevocacion.NO_VERIFICADA
        var detalle: String? = null
        if (verificarRevocacion && motivo == null) {
            val consulta = consultarOcsp(cert, emisor)
            estadoRevocacion = consulta.first
            detalle = consulta.second
        }

        val textoRevocacion = detalle?.let { " · OCSP: $it" }
            ?: " · revocación: ${estadoRevocacion.name.lowercase(Locale.ROOT).replace('_', ' ')}"
        val resumen = "Certificado ${formatear(notBefore)} → ${formatear(notAfter)}" +
            " · $diasRestantes día(s) restantes$textoRevocacion"

        return Resultado(
            vigente = motivo == null,
            motivo = motivo,
            notBefore = notBefore,
            notAfter = notAfter,
            diasRestantes = diasRestantes,
            revocacion = estadoRevocacion,
            detalleRevocacion = detalle,
            resumen = resumen
        )
    }

    /** Consulta OCSP usando la URL publicada en la extensión AIA del certificado. */
    private fun consultarOcsp(
        cert: X509Certificate,
        emisor: X509Certificate?
    ): Pair<EstadoRevocacion, String?> {
        if (emisor == null) {
            return EstadoRevocacion.ERROR to "no hay certificado emisor en la cadena del token"
        }
        val urlOcsp = obtenerUrlOcsp(cert)
            ?: return EstadoRevocacion.SIN_OCSP to "el certificado no publica URL OCSP (extensión AIA)"

        return try {
            val digest = JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build()
                .get(CertificateID.HASH_SHA1)
            val id = JcaCertificateID(digest, cert, emisor.serialNumber)
            val peticion = OCSPReqBuilder().addRequest(id).build()

            val conexion = URL(urlOcsp).openConnection() as HttpURLConnection
            conexion.requestMethod = "POST"
            conexion.doOutput = true
            conexion.connectTimeout = 4000
            conexion.readTimeout = 4000
            conexion.setRequestProperty("Content-Type", "application/ocsp-request")
            conexion.outputStream.use { it.write(peticion.encoded) }

            val respuesta = OCSPResp(conexion.inputStream.readBytes())
            if (respuesta.status != OCSPResp.SUCCESSFUL) {
                return EstadoRevocacion.ERROR to "el servidor OCSP devolvió estado ${respuesta.status}"
            }
            val basica = respuesta.responseObject as? BasicOCSPResp
                ?: return EstadoRevocacion.ERROR to "respuesta OCSP inesperada"
            val simple = basica.responses.firstOrNull()
                ?: return EstadoRevocacion.DESCONOCIDA to "la respuesta OCSP no trae resultados"

            when (val estado = simple.certStatus) {
                is RevokedStatus ->
                    EstadoRevocacion.REVOCADA to "revocado el ${formatear(estado.revocationTime)}"
                CertificateStatus.GOOD ->
                    EstadoRevocacion.VIGENTE to "no revocado"
                else ->
                    EstadoRevocacion.DESCONOCIDA to "estado desconocido"
            }
        } catch (e: Exception) {
            EstadoRevocacion.ERROR to
                "no se pudo consultar OCSP (${e.message ?: e.javaClass.simpleName})"
        }
    }

    private fun obtenerUrlOcsp(cert: X509Certificate): String? {
        val valor = cert.getExtensionValue(Extension.authorityInfoAccess.id) ?: return null
        val bytes = ASN1OctetString.getInstance(valor).octets
        val aia = AuthorityInformationAccess.getInstance(ASN1Primitive.fromByteArray(bytes))
        return aia.accessDescriptions
            .firstOrNull { it.accessMethod == AccessDescription.id_ad_ocsp }
            ?.accessLocation
            ?.takeIf { it.tagNo == GeneralName.uniformResourceIdentifier }
            ?.let { (it.name as? ASN1IA5String)?.string }
    }

    /** Igual comparación que usa el validador de AGESIC: fecha actual dentro de [notBefore, notAfter]. */
    fun estaDentroDeValidez(cert: X509Certificate, fecha: Date = Date()): Boolean =
        !fecha.before(cert.notBefore) && !fecha.after(cert.notAfter)

    private fun formatear(fecha: Date): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-UY")).format(fecha)
}
