package com.lnantes.uifirmaelectronica

import com.lnantes.uifirmaelectronica.core.util.ReceiptFileName
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptFileNameTest {

    @Test
    fun `nombre incremental usa cedula fecha y secuencia`() {
        val base = ReceiptFileName.base("45227453", "20261115", 3)
        assertEquals("recibo_45227453_20261115_03", base)
        assertEquals("recibo_45227453_20261115_03.pdf", ReceiptFileName.nombre(base))
        assertEquals(
            "recibo_45227453_20261115_03_firmado_avanzada.pdf",
            ReceiptFileName.nombre(base, "firmado_avanzada"),
        )
    }

    @Test
    fun `normaliza cedula y fecha de Laravel`() {
        assertEquals("45227453", ReceiptFileName.cedula("4.522.745-3", "6B1E"))
        assertEquals("6B1E", ReceiptFileName.cedula("", "6B1E"))
        assertEquals("20261115", ReceiptFileName.fechaCompacta("2026-11-15", "20260101"))
        assertEquals("20261115", ReceiptFileName.fechaCompacta("15/11/2026", "20260101"))
        assertEquals("20261115", ReceiptFileName.fechaCompacta("20261115", "20260101"))
        assertEquals("20260101", ReceiptFileName.fechaCompacta("", "20260101"))
    }
}
