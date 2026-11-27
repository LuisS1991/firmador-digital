package com.lnantes.uifirmaelectronica.core.model

/**
 *Estado del recibo dentro del ciclo de firma avanzada.
 */
enum class ReceiptStatus { PENDING, SIGNED }

/**
 * Recibo generado en local y, opcionalmente, firmado con token físico.
 */
data class Receipt(
    val id: String,
    val number: String,
    val concept: String,
    val amount: String,
    val signer: String,
    /** Funcionario que entrega la indumentaria (campo `operador` de la API). */
    val entregador: String = "",
    val issuedAt: String,
    val fileName: String,
    val status: ReceiptStatus,
    val signedAt: String? = null,
    val tokenSerial: String? = null,
)

/**
 * Resultado de una operación de intercambio con otra estación.
 */
data class ExchangeResult(
    val operation: ExchangeOperation,
    val processed: Int,
    val detail: String,
    val completedAt: String,
)

enum class ExchangeOperation { EXPORT, IMPORT }
