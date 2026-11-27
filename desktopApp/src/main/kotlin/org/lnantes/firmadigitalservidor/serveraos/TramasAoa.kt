package org.lnantes.firmadigitalservidor.serveraos

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Framing compartido por el canal AOA: [longitud:4 bytes big-endian][payload UTF-8].
 * Es el mismo formato que ya usaba el móvil para los trazos, ahora aplicado
 * también a los comandos PC → móvil.
 */
object TramasAoa {
    const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    const val CABECERA_BYTES = 4

    fun construir(payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_PAYLOAD_BYTES) {
            "Tamaño de trama inválido: ${payload.size}"
        }
        val cabecera = ByteBuffer.allocate(CABECERA_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size)
            .array()
        return cabecera + payload
    }
}

/**
 * Reensambla los chunks que llegan por bulk IN. replicate exacta del comportamiento
 * anterior: una longitud inválida descarta todo el buffer acumulado.
 */
class AcumuladorTramas {
    private val buffer = ByteArrayOutputStream()

    fun alimentar(chunk: ByteArray): List<ByteArray> {
        buffer.write(chunk, 0, chunk.size)
        val completas = mutableListOf<ByteArray>()

        while (buffer.size() >= TramasAoa.CABECERA_BYTES) {
            val datos = buffer.toByteArray()
            val longitud = ((datos[0].toInt() and 0xFF) shl 24) or
                ((datos[1].toInt() and 0xFF) shl 16) or
                ((datos[2].toInt() and 0xFF) shl 8) or
                (datos[3].toInt() and 0xFF)

            if (longitud <= 0 || longitud > TramasAoa.MAX_PAYLOAD_BYTES) {
                reiniciar()
                break
            }

            val total = TramasAoa.CABECERA_BYTES + longitud
            if (datos.size < total) break

            completas += datos.copyOfRange(TramasAoa.CABECERA_BYTES, total)
            val resto = datos.copyOfRange(total, datos.size)
            reiniciar()
            buffer.write(resto, 0, resto.size)
        }

        return completas
    }

    fun reiniciar() {
        buffer.reset()
    }
}
