package ru.dmdp.tishina.core.domain.model

/**
 * One entry of the static OSS-license inventory rendered by AboutScreen (FR-21).
 *
 * Plain Kotlin data class — no serialization annotations live here on purpose, so the
 * `:core:domain` module stays free of Android / JSON dependencies. The parser in
 * `:core:data` keeps a private `@Serializable` DTO and maps to this type.
 */
data class OssLicense(val name: String, val version: String, val license: String, val url: String)
