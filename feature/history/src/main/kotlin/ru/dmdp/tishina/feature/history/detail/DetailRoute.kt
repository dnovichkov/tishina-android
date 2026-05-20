package ru.dmdp.tishina.feature.history.detail

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation argument for the Detail screen. Wraps a single [measurementId] so
 * `SavedStateHandle.toRoute<DetailRoute>()` can decode it without string-keyed lookups.
 *
 * Declared inside `:feature:history` (not `:app/navigation`) because the feature module owns
 * both the screen and the ViewModel; `:app` only wires the destination into the NavHost.
 */
@Serializable
data class DetailRoute(val measurementId: Long)
