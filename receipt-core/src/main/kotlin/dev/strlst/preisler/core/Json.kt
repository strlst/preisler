package dev.strlst.preisler.core

import kotlinx.serialization.json.Json

/** Shared JSON configuration for fixtures, export and the LAN payload. */
val PreislerJson: Json =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
