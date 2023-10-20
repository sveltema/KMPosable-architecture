package com.labosu.kmposable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform