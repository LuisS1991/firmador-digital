package com.lnantes.uifirmaelectronica

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform