# Reglas adicionales para el binario release de Compose Desktop.
# El terror de ProGuard aquí no es el tamaño sino no romper las cargas por reflexión.

# Código de la aplicación: se conserva completo (SQLite, AOA, PDF, PKCS#11, REST).
-keep class com.lnantes.uifirmaelectronica.** { *; }
-keep class org.lnantes.firmadigitalservidor.** { *; }

# org.sqlite.JDBC se carga con Class.forName("org.sqlite.JDBC").
-keep class org.sqlite.** { *; }

# Bibliotecas que cargan clases/proveedores de forma dinámica.
-keep class org.usb4java.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.engio.** { *; }
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.slf4j.** { *; }
# PDFBox usa commons-logging, que instancia LogFactoryImpl por nombre.
-keep class org.apache.commons.logging.** { *; }

# Serialización y reflexión de Kotlin.
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses, Exceptions
-keep class **$$serializer { *; }
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Dependencias opcionales que no están en el runtime y no se usan.
# Sin estos -dontwarn ProGuard 7.8 aborta con "Please correct the above warnings first".
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.log.**
-dontwarn javax.servlet.**
-dontwarn net.engio.mbassy.**
-dontwarn android.os.**
-dontwarn com.ibm.security.jgss.**
-dontwarn org.apache.pdfbox.**

# Notas informativas que no afectan al ejecutable.
-dontnote org.apache.commons.logging.**
-dontnote net.engio.**
-dontnote com.hierynomus.**
-dontnote org.apache.pdfbox.**
