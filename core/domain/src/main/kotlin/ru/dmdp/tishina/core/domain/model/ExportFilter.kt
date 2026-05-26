package ru.dmdp.tishina.core.domain.model

/**
 * Narrows which measurements are written by an FR-20 CSV export.
 *
 * Two cases today:
 *  - [All] — every row currently in the history (no filter).
 *  - [ByIds] — only the rows whose `id` is in [ByIds.ids].
 *
 * Sealed-interface so future v1.1 filters (date range, text search in `note`,
 * minimum-dB threshold) extend it without breaking exhaustive `when` callers.
 *
 * `ByIds` accepts an empty set on purpose — the export then emits a header-only
 * file. The use-case does not short-circuit; the choice belongs to the caller.
 */
sealed interface ExportFilter {

    data object All : ExportFilter

    data class ByIds(val ids: Set<Long>) : ExportFilter
}
