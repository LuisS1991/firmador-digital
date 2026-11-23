# UIFirmaElectronica

UI de escritorio (Compose Multiplatform / Kotlin) para una aplicación de **firma digital común y avanzada**.
Este repositorio contiene **sólo la capa gráfica y la orquestación de estado**; la lógica de negocio se
conecta mediante interfaces.

## Arquitectura

```
shared/src/commonMain/kotlin/com/lnantes/uifirmaelectronica/
├── core/
│   ├── model/            Modelos inmutables (modos, firma, recibos, logs)
│   ├── navigation/       Destinos e historial de navegación
│   ├── services/         CONTRATOS que debe implementar el backend
│   └── util/             Formateadores multiplataforma
├── data/demo/            Implementaciones simuladas (sólo para ver la UI)
├── ui/
│   ├── theme/            Colores, tipografía e iconos vectoriales propios
│   ├── components/       Componentes reutilizables y animaciones
│   └── presentation/     Presenters: estado inmutable + eventos (sin Compose)
└── views/                Pantallas por dominio
    ├── home/
    ├── common/
    ├── advanced/
    ├── receipts/
    └── exchange/
```

`desktopApp` es el módulo de arranque de la ventana de escritorio.

## Vistas y feedback visual

| Vista | Interacciones | Indicadores |
| --- | --- | --- |
| **Inicio** | Selección de modo, accesos rápidos | Tarjetas de modo, estado del sistema |
| **Firma común** | Sincronizar tablet, capturar firma, previsualizar, firmar documento | Animación PC ↔ tablet con puntos en secuencia, diálogo de firma, stepper, registro de eventos, spinner por acción |
| **Firma avanzada** | Generar recibo PDF local, firmar con token físico | Animación de token con pulsos y flujo de datos, stepper, registro, resumen del recibo |
| **Recibos firmados** | Buscar, filtrar, ver detalle y PDF simulado | Lista con estado, diálogo de detalle, avisos inline |
| **Intercambiar recibos** | Flujo en 3 pasos: buscar terminal (red o nombre + IP), sincronizarla y seleccionar recibos locales a exportar / remotos a importar | Stepper, animación PC ↔ PC con nombres/IP, checkboxes y contadores, banner de «Transacción finalizada», registro de transferencia |

El registro de operaciones muestra mensajes como *"Estableciendo enlace seguro…"*,
*"Sincronización correcta"*, *"Firma capturada correctamente"* o *"Enviando datos al repositorio local…"*.

## Conectar tu backend

1. Implementa las interfaces de `core/services/SignatureServices.kt`:
   - `TabletSyncService` → detección y sincronización con la tablet.
   - `SignatureCaptureService` → captura de la firma.
   - `DocumentSigningService` → documento actual y aplicación de firma común.
   - `ReceiptService` → generación, listado (`StateFlow`) y firma con token.
   - `ReceiptExchangeService` → descubrimiento de estaciones (`discoverStations`), sincronización de la terminal elegida (`synchronize`), consulta de recibos remotos (`listRemoteReceipts`) y exportación/importación de la selección de recibos. El modelo `RemoteStation` incluye nombre, IP y estado. Las listas de recibos sólo se habilitan después de sincronizar.
2. Crea una implementación de `SignatureServices` que las agrupe.
3. En `shared/src/commonMain/kotlin/com/lnantes/uifirmaelectronica/App.kt`, cambia:

```kotlin
val services = remember { DemoSignatureServices() }   // antes
val services = remember { MiServicioReal() }          // después
```

No hace falta tocar ninguna vista: los presenters sólo conocen las interfaces.

## Ejecutar

```bash
./gradlew :desktopApp:run          # aplicación de escritorio
./gradlew :shared:jvmTest          # pruebas
./gradlew :desktopApp:hotRun --auto  # hot reload
```
