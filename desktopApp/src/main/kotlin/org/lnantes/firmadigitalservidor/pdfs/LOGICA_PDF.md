# Generación local del recibo firmado

`GeneradorReciboPdf.kt` es el helper para producir el recibo con las dos firmas
sin salir de la máquina: no hace POST, no consulta SQLite ni abre ventanas.
Toda la comunicación con el móvil/AOA sigue en `serveraos`.

## Recursos usados

- Plantilla: `src/main/resources/org/lnantes/firmadigitalservidor/docs/455527453.pdf` (A4, 596 x 842 pt).
- Sello visible de la firma avanzada: `src/main/resources/org/lnantes/firmadigitalservidor/icons/SELLO.png`.

Si se ejecuta desde un JAR, el helper extrae ambos recursos del classpath a un
archivo temporal; no depende del directorio de trabajo.

## Dónde van las firmas

Las zonas son `ZonasRecibo.ENTREGA` (izquierda, «Entrega: Firma:») y
`ZonasRecibo.RECIBE` (derecha, «Recibe: Firma:»), en coordenadas PDF estándar
(origen abajo-izquierda). Se adaptan automáticamente si la página no es A4.

- Firma avanzada (token/cédula, PAdES) → columna **Entrega**, con `SELLO.png`
  dibujado dentro del appearance de la firma.
- Firma común (trazos del móvil) → columna **Recibe**, dibujada como vectores.

## Orden recomendado

`generarReciboFirmado()` hace: trazos comunes sobre una copia temporal →
firma PAdES sobre ese resultado → borra el temporal. Así la firma criptográfica
cubre también la firma dibujada. **No firmar primero y estampar después**,
porque un cambio posterior rompería la integridad PAdES.

## Uso mínimo

```kotlin
val generador = GeneradorReciboPdf(onLog = { println(it) })
val archivo = generador.generarReciboFirmado(
    pdfSalida = File("recibo_firmado.pdf"),
    contenido = contenidoTextoFirmaRecibidoDelMovil,
    firmador = FirmadorAvanzadoPkcs11(rutaLibreria),
    pin = pinLocal
)
```

Para firmar solo un tipo: `estamparFirmaComun()` o `estamparFirmaAvanzada()`.
Si el dispositivo PKCS#11 ya está inicializado, pase `inicializar = false` en
la variante avanzada y libere el firmador desde el llamador.

`FirmadorPDF.estamparFirma()` acepta `sello` y `zonaSello` para reutilizar esta
misma presentación desde el flujo de firma avanzada actual.
