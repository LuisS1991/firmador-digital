package com.lnantes.uifirmaelectronica.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal val backendJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun JsonElement.objeto(): JsonObject? = this as? JsonObject

internal fun JsonElement.arreglo(): JsonArray? = this as? JsonArray

internal fun JsonObject.texto(vararg claves: String): String =
    claves.firstNotNullOfOrNull { clave ->
        (this[clave] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }.orEmpty()

internal fun JsonObject.numero(vararg claves: String): Long =
    claves.firstNotNullOfOrNull { clave ->
        (this[clave] as? JsonPrimitive)?.longOrNull
            ?: (this[clave] as? JsonPrimitive)?.doubleOrNull?.toLong()
    } ?: 0L

internal fun JsonObject.booleano(vararg claves: String): Boolean =
    claves.firstNotNullOfOrNull { clave -> (this[clave] as? JsonPrimitive)?.booleanOrNull } ?: false

internal fun JsonObject.subobjeto(vararg claves: String): JsonObject =
    claves.firstNotNullOfOrNull { clave -> this[clave]?.objeto() } ?: JsonObject(emptyMap())
