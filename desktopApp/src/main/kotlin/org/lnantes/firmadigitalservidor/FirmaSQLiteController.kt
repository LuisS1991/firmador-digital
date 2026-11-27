package org.lnantes.firmadigitalservidor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import org.lnantes.firmadigitalservidor.ConsentimientoFirma

class FirmaSQLiteController(
    rutaBaseDatos: String = File(System.getProperty("user.dir"), "firmas.db").absolutePath
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    //private val url = "jdbc:sqlite:$rutaBaseDatos"
    //val nombreArchivo: String = File(rutaBaseDatos).name
    private val archivoBD = File(rutaBaseDatos).absoluteFile

    private val url: String = "jdbc:sqlite:${archivoBD.absolutePath}"

    val nombreArchivo: String = archivoBD.name

    /**
     * Un binario generado con un runtime jlink incompleto puede no traer
     * `java.sql`. En ese caso no queremos que la ventana entera muera al abrir:
     * se avisa por stderr y el error concreto se lanza cuando se intenta guardar.
     */
    private val errorBaseDatos: Throwable? = runCatching { crearTabla() }
        .onFailure { error ->
            System.err.println(
                "⚠️ SQLite no disponible (${error::class.simpleName}: ${error.message}). " +
                    "El binario debe incluir el módulo java.sql para guardar firmas en local."
            )
        }
        .exceptionOrNull()

    @Synchronized
    fun guardar(codigo: String, payload: PayloadFirma) {
        val sql = """
            INSERT INTO firmas_recibidas
                (codigo, timestamp, tamano_bytes, contenido_texto, firma_base64, fecha_registro)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(codigo) DO UPDATE SET
                timestamp = excluded.timestamp,
                tamano_bytes = excluded.tamano_bytes,
                contenido_texto = excluded.contenido_texto,
                firma_base64 = excluded.firma_base64,
                fecha_registro = excluded.fecha_registro,
                enviado = 0,
                fecha_envio = NULL,
                respuesta_envio = NULL
        """.trimIndent()

        conectar().use { conexion ->
            conexion.prepareStatement(sql).use { sentencia ->
                sentencia.setString(1, codigo)
                sentencia.setString(2, payload.timestamp)
                sentencia.setInt(3, payload.tamanoBytes)
                sentencia.setString(4, json.encodeToString(payload.contenidoTexto))
                sentencia.setString(5, payload.firmaBase64)
                sentencia.setString(6, Instant.now().toString())
                sentencia.executeUpdate()
            }
        }
    }

    /**
     * Persiste el consentimiento que el móvil envió adjunto a la firma.
     * [origen] es `comun` (trazos) o `avanzada` (respuesta de PIN).
     */
    @Synchronized
    fun guardarConsentimiento(
        codigo: String?,
        consentimiento: ConsentimientoFirma,
        origen: String
    ) {
        val sql = """
            INSERT INTO consentimientos
                (codigo, origen, tipo, otorgado, fecha, normativa_notificada,
                 normativa_id, id_solicitud, fecha_registro)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conectar().use { conexion ->
            conexion.prepareStatement(sql).use { sentencia ->
                sentencia.setString(1, codigo)
                sentencia.setString(2, origen)
                sentencia.setString(3, consentimiento.tipo)
                sentencia.setInt(4, if (consentimiento.otorgado) 1 else 0)
                sentencia.setLong(5, consentimiento.fecha)
                sentencia.setInt(6, if (consentimiento.normativaNotificada) 1 else 0)
                sentencia.setString(7, consentimiento.normativaId)
                sentencia.setString(8, consentimiento.idSolicitud)
                sentencia.setString(9, Instant.now().toString())
                sentencia.executeUpdate()
            }
        }
    }

    @Synchronized
    fun contarConsentimientos(): Int {
        conectar().use { conexion ->
            conexion.createStatement().use { sentencia ->
                sentencia.executeQuery("SELECT COUNT(*) FROM consentimientos").use { resultado ->
                    return if (resultado.next()) resultado.getInt(1) else 0
                }
            }
        }
    }

    @Synchronized
    fun marcarEnvio(codigo: String, ok: Boolean, respuesta: String) {
        val sql = """
            UPDATE firmas_recibidas
            SET enviado = ?, fecha_envio = ?, respuesta_envio = ?
            WHERE codigo = ?
        """.trimIndent()

        conectar().use { conexion ->
            conexion.prepareStatement(sql).use { sentencia ->
                sentencia.setInt(1, if (ok) 1 else 0)
                sentencia.setString(2, Instant.now().toString())
                sentencia.setString(3, respuesta)
                sentencia.setString(4, codigo)
                sentencia.executeUpdate()
            }
        }
    }

    @Synchronized
    fun obtener(codigo: String): FirmaRegistro? {
        val sql = "SELECT * FROM firmas_recibidas WHERE codigo = ?"
        conectar().use { conexion ->
            conexion.prepareStatement(sql).use { sentencia ->
                sentencia.setString(1, codigo)
                sentencia.executeQuery().use { resultado ->
                    return if (resultado.next()) resultado.aRegistro() else null
                }
            }
        }
    }

    @Synchronized
    fun listar(): List<FirmaRegistro> {
        val registros = mutableListOf<FirmaRegistro>()
        conectar().use { conexion ->
            conexion.createStatement().use { sentencia ->
                sentencia.executeQuery("SELECT * FROM firmas_recibidas ORDER BY fecha_registro DESC")
                    .use { resultado ->
                        while (resultado.next()) {
                            registros.add(resultado.aRegistro())
                        }
                    }
            }
        }
        return registros
    }

    @Synchronized
    fun contar(): Int {
        conectar().use { conexion ->
            conexion.createStatement().use { sentencia ->
                sentencia.executeQuery("SELECT COUNT(*) FROM firmas_recibidas").use { resultado ->
                    return if (resultado.next()) resultado.getInt(1) else 0
                }
            }
        }
    }

    private fun crearTabla() {
        val sql = """
            CREATE TABLE IF NOT EXISTS firmas_recibidas (
                codigo TEXT PRIMARY KEY NOT NULL,
                timestamp TEXT NOT NULL,
                tamano_bytes INTEGER NOT NULL,
                contenido_texto TEXT NOT NULL,
                firma_base64 TEXT NOT NULL,
                fecha_registro TEXT NOT NULL,
                enviado INTEGER NOT NULL DEFAULT 0,
                fecha_envio TEXT,
                respuesta_envio TEXT
            )
        """.trimIndent()

        val sqlConsentimientos = """
            CREATE TABLE IF NOT EXISTS consentimientos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo TEXT,
                origen TEXT NOT NULL,
                tipo TEXT NOT NULL,
                otorgado INTEGER NOT NULL,
                fecha INTEGER NOT NULL,
                normativa_notificada INTEGER NOT NULL,
                normativa_id TEXT NOT NULL,
                id_solicitud TEXT,
                fecha_registro TEXT NOT NULL
            )
        """.trimIndent()

        conectar().use { conexion ->
            conexion.createStatement().use { sentencia ->
                sentencia.execute(sql)
                sentencia.execute(sqlConsentimientos)
            }
        }
    }

    private fun conectar(): Connection {
        errorBaseDatos?.let { fallo ->
            throw IllegalStateException(
                "SQLite no está disponible en este runtime; reinstale el binario " +
                    "generado con el módulo java.sql.",
                fallo,
            )
        }
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection(url)
    }


    private fun ResultSet.aRegistro(): FirmaRegistro = FirmaRegistro(
        codigo = getString("codigo"),
        timestamp = getString("timestamp"),
        tamanoBytes = getInt("tamano_bytes"),
        contenidoTexto = getString("contenido_texto"),
        firmaBase64 = getString("firma_base64"),
        fechaRegistro = getString("fecha_registro"),
        enviado = getInt("enviado") == 1,
        fechaEnvio = getString("fecha_envio"),
        respuestaEnvio = getString("respuesta_envio")
    )
}

data class FirmaRegistro(
    val codigo: String,
    val timestamp: String,
    val tamanoBytes: Int,
    val contenidoTexto: String,
    val firmaBase64: String,
    val fechaRegistro: String,
    val enviado: Boolean,
    val fechaEnvio: String?,
    val respuestaEnvio: String?
)
