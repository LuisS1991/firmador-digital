import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)

    // Kotlinx Serialization JSON
    implementation(libs.kotlinx.serializationJson)
    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    // Apache PDFBox 3
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    // Bouncy Castle Provider / PKIX / CMS
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // AOA (USB) para la tablet de captura
    implementation("org.usb4java:usb4java:1.3.0")
    // Carpeta compartida TIC Desarrollo
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.lnantes.uifirmaelectronica.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.lnantes.uifirmaelectronica"
            packageVersion = "1.0.0"

            // El runtime por defecto de Compose sólo trae java.base, java.desktop,
            // java.logging y jdk.crypto.ec. La app necesita además JDBC (SQLite),
            // HttpClient (Laravel), JNDI (SMBJ) y el proveedor PKCS#11 del token.
            modules(
                "java.sql",
                "java.net.http",
                "java.naming",
                "java.xml",
                "jdk.crypto.cryptoki",
                "jdk.unsupported",
            )
            // Por si una dependencia carga otra clase de forma reflexiva, el
            // binario lleva el JDK completo. Evita NoClassDefFoundError en campo.
            includeAllModules = true
        }

        buildTypes.release.proguard {
            // Desactivado: ProGuard reprocesa los jars de BouncyCastle y rompe
            // sus firmas META-INF (*.SF/*.DSA), provocando en runtime
            // "SHA-256 digest error for org/bouncycastle/cms/CMSSignedGenerator.class".
            isEnabled.set(false)
            configurationFiles.from(file("proguard-rules.pro"))
        }
    }
}