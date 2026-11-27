# UIFirmaElectronica

UI de escritorio (Compose Multiplatform / Kotlin) para una aplicación de **firma digital común y avanzada**.
`shared` contiene la capa gráfica y los contratos; `desktopApp` inyecta la implementación JVM real,
donde vive toda la lógica nativa (PDFBox, PKCS#11, SQLite, SMB y AOA/USB). **El acto de firmar es local**:
la API sólo entrega datos del recibo y recibe el payload firmado.

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

`desktopApp` es el módulo de arranque de la ventana de escritorio y del backend real:

```
desktopApp/src/main/kotlin/
├── com/lnantes/uifirmaelectronica/backend/   Implementación JVM de SignatureServices
│   ├── BackendConfig.kt                      Configuración por archivo/env/system property
│   ├── ApiGateway.kt                         GET del recibo a Laravel
│   ├── ReceiptPdfGenerator.kt                PDF nuevo dibujado desde los datos de la API
│   └── DesktopSignatureServices.kt           SQLite, TCP, AOA, PAdES, REST y compartida
└── org/lnantes/firmadigitalservidor/         Capas portadas del servidor anterior
```

Recurso: `desktopApp/src/main/resources/org/lnantes/firmadigitalservidor/icons/SELLO.png`.
El PDF `docs/455527453.pdf` se conserva sólo como referencia del layout: **no se carga para generar
los recibos**, porque era un comprobante ya relleno y superponerlo dejaba datos ajenos visibles.

## Vistas y feedback visual

| Vista | Interacciones | Indicadores |
| --- | --- | --- |
| **Inicio** | Selección de modo, accesos rápidos | Tarjetas de modo, estado del sistema |
| **Firma común** | Sincronizar tablet, capturar firma, previsualizar, firmar documento | Animación PC ↔ tablet con puntos en secuencia, diálogo de firma, stepper, registro de eventos, spinner por acción |
| **Firma avanzada** | Generar recibo PDF local, firmar con token físico | Animación de token con pulsos y flujo de datos, stepper, registro, resumen del recibo |
| **Recibos firmados** | Buscar, filtrar, ver detalle y PDF simulado | Lista con estado, diálogo de detalle, avisos inline |
| **Intercambiar recibos** | Flujo en 3 pasos: buscar terminal (red o nombre + IP), sincronizarla y seleccionar recibos locales a exportar / remotos a importar | Stepper, animación PC ↔ PC con nombres/IP, checkboxes y contadores, banner de «Transacción finalizada», registro de transferencia |
| **Firma desde enlace** | Dos firmantes (quien recibe y el operador), cada uno elige común o avanzada; reintento AOA y limpieza de temporales | Stepper, orden numerado 1 → 2, indicador de columna, animación de tablet/token, botón «Reintentar conexión AOA», lista de archivos |

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
3. En `desktopApp/src/main/kotlin/com/lnantes/uifirmaelectronica/main.kt`, cambia `createDesktopSignatureServices()`
   por la fábrica que corresponda. `App(services = ...)` ya recibe la implementación por inyección.

No hace falta tocar ninguna vista: los presenters sólo conocen las interfaces.

### Backend real incluido

`createDesktopSignatureServices()` usa la configuración de `config/firmador.properties` (o variables
`FIRMADOR_*` / system properties). El flujo es:

1. **Generación del recibo:** el campo *Código del recibo* llama a la ruta nueva
   `http://localhost:8000/devolucion/generar/desktop/recibo/{codigo}`. La ruta anterior
   `/devolucion/generar/recibo/{codigo}` queda sólo como alternativa si algún entorno no se actualizó.
   Con los datos de la API se dibuja un PDF A4 nuevo (sin plantillas ni rectángulos que tapen texto):
   encabezado, datos del funcionario, tabla de prendas, columnas *Entrega*/*Recibe* y bloque de
   verificación con el código. El nombre del operador sale del campo `operador`.
2. **Firma avanzada:** se pide el PIN al móvil por AOA (o `FIRMADOR_PIN_LOCAL`), se firma PAdES con el
   token y se estampa `SELLO.png` en el espacio «Firma:» de la columna elegida (*Entrega* o *Recibe*).
3. **Firma común:** los trazos llegan por AOA desde el móvil, se guardan en SQLite antes del POST y se
   estampan como vectores en el espacio «Firma:» de la columna elegida. El móvil envía el tamaño real
   del lienzo (`ancho_area`/`alto_area`) junto con los trazos, así se respeta la posición relativa de
   cada punto y se usan extremos redondos como en la pantalla táctil. Los payloads antiguos sin esos
   campos usan 720 x 1280 px como referencia. Luego se envía el JSON comprimido con gzip a
   `/devoluciones/recibos/firmar`, con `token`, `firmante` y `lado`. Laravel valida el token (vigencia
   de 3 minutos) y lo consume para impedir reutilización: el POST se hace una sola vez por recibo y las
   firmas restantes quedan en el PDF local. La captura descarta el payload anterior para esperar los
   trazos de la segunda persona.
4. **Servidor TCP:** en el puerto `5500` PHP puede consultar `firma_base64` por código (una petición
   por conexión, terminador `\r\n`). La aplicación lo inicia con `firmador.tcp.habilitado=true`; también
   puede ejecutarse de forma independiente con la clase `org.lnantes.firmadigitalservidor.server.ServidorTcpMainKt`.
5. **Compartida:** `config/compartida.properties` apunta a `\\10.100.52.4\tic-desarollo`. La password
   puede venir de Seahorse mediante `compartida.password.comando` (por ejemplo `secret-tool`), de
   `COMPARTIDA_PASSWORD` o del modo `local`. Nunca se escribe en los logs.

### Dos formas de abrir la aplicación

**1) Escritorio normal.** Sin argumentos:

```bash
./gradlew :desktopApp:run
```

Entra al `AppScaffold` completo: Inicio, Firma común, Firma avanzada, Recibos firmados e Intercambiar
recibos, con la lógica actual de cada vista.

**2) Desde el enlace profundo.** El ejecutable acepta el enlace que genera Laravel:

```bash
./gradlew :desktopApp:run --args='firmaapp://firmar?id=D896-C2E5-27D7&token=<token>'
```

Abre una pantalla de firma guiada (no el panel completo) y hace lo siguiente:

1. Obtiene los datos del `id` desde Laravel y genera el PDF desde cero, sin usar la plantilla rellena.
2. Muestra los datos del recibo (quien recibe, quien entrega, concepto, fecha, código) y presenta dos
   firmantes en orden fijo: **1 · Quien recibe la ropa** y **2 · Funcionario que entrega (operador)**.
   Cada uno elige libremente **firma común (trazos)** o **firma avanzada (token)**: la normativa exige
   ofrecer ambas opciones a cualquier persona que firme. La UI indica el orden y la columna del recibo
   donde quedará cada firma (*Recibe* / *Entrega*).
3. Captura en ese orden. Los trazos se estampan en su columna y se envían por gzip a Laravel con el
   token; las firmas PAdES se estampan al final para que ningún trazo posterior rompa la integridad
   digital. El archivo conserva trazos, sello y firma digital.
4. Al terminar se borran el PDF base y los intermedios; **sólo queda el archivo final firmado**.

Si falla el enlace AOA, la pantalla ofrece **«Reintentar conexión AOA»**: reinicia el handshake sin
recrear la ventana y permite continuar con la firma pendiente.

Los archivos intermedios usan
`recibo_<cedula>_<fecha>_<secuencia>_firmado_recibe_comun.pdf`,
`..._firmado_recibe_avanzada.pdf`, `..._firmado_entrega_comun.pdf` y
`..._firmado_entrega_avanzada.pdf`; por ejemplo `recibo_45227453_20261115_01_firmado_entrega_avanzada.pdf`.
La secuencia se calcula mirando los PDF ya existentes, así que se pueden generar varios recibos para la
misma persona y fecha sin sobrescribir nada. **Un recibo firmado no se puede firmar otra vez**: una vez
estampada una firma, el botón queda desactivado y el backend rechaza un segundo intento con un mensaje
claro. Para volver a firmar hay que generar un recibo nuevo (botón *Generar el recibo de nuevo*).

El `id`/código se muestra en la pantalla guiada y el `token` se guarda sólo en memoria para agregarlo
al POST de firma; nunca se muestra en la UI ni se escribe en logs o en `config/firmador.properties`.
Laravel lo invalida a los
3 minutos, así que conviene firmar enseguida. El botón *Abrir aplicación completa* sale del modo enlace
profundo y lleva a la UI normal sin perder el código.

Por compatibilidad también se aceptan `app://firmar?codigo=...`, `--codigo=...`, `--token=...` y el código
suelto; esas formas entran igualmente en el modo guiado de firma.

## Ejecutar

```bash
./gradlew :desktopApp:run          # aplicación de escritorio
./gradlew :shared:jvmTest :desktopApp:test   # pruebas del presentador y del PDF generado
./gradlew :desktopApp:hotRun --auto  # hot reload
```

### Generar el binario

```bash
./gradlew :desktopApp:createDistributable        # carpeta ejecutable
./gradlew :desktopApp:packageDeb                 # paquete .deb (Linux)
./gradlew :desktopApp:packageMsi                 # MSI (Windows)
./gradlew :desktopApp:packageDmg                 # DMG (macOS)

# Variantes release (ProGuard activo):
./gradlew :desktopApp:createReleaseDistributable
./gradlew :desktopApp:packageReleaseDeb
```

`desktopApp/build.gradle.kts` configura el runtime jlink con `includeAllModules = true` y, de forma
explícita, `java.sql`, `java.net.http`, `java.naming`, `java.xml`, `jdk.crypto.cryptoki` y
`jdk.unsupported`. Sin esos módulos el ejecutable falla al arrancar con
`NoClassDefFoundError: java/sql/Driver` (SQLite), al consultar Laravel con `java/net/http/HttpClient`
o al firmar con el token PKCS#11. Si se ejecuta un JAR suelto, hágalo con un JDK completo (21 o superior),
no con un JRE reducido. Si falta `java.sql`, la ventana ahora abre pero la firma se aborta con un mensaje
claro en lugar de cerrarse abruptamente. Lo mismo pasa si el puerto TCP `5500` está ocupado por otra
instancia: se avisa por log y la ventana sigue abierta.

Las reglas de ProGuard para el release están en `desktopApp/proguard-rules.pro`. Conservan las clases
cargadas por nombre (`org.sqlite.JDBC`, `LogFactoryImpl` de PDFBox, proveedores PKCS#11, USB, SMB y
serializadores) y silencian las advertencias de dependencias opcionales ausentes; sin ellas ProGuard
7.8 aborta con `Please correct the above warnings first` y el binario generado falla al iniciar.
