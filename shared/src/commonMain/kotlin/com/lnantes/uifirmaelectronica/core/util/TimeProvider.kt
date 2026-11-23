package com.lnantes.uifirmaelectronica.core.util

/**
 * Reloj multiplataforma. Cada target proporciona su implementación;
 * el backend real puede sustituirlo por su propio proveedor de tiempo.
 */
expect fun currentTimeMillis(): Long
