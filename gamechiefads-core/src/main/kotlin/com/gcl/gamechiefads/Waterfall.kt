package com.gcl.gamechiefads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tries ordered unit ids; returns the first non-null result, or null.
 * Each tier has its own timeout and the whole run has an overall budget. Errors count as a miss.
 * The loader must release whatever it loaded if it is cancelled (use invokeOnCancellation).
 */
internal object Waterfall {

    suspend fun <T : Any> run(
        units: List<String>,
        tierTimeoutMs: Long,
        overallBudgetMs: Long,
        load: suspend (unitId: String) -> T?,
    ): T? = withTimeoutOrNull(overallBudgetMs) {
        for (unit in units) {
            if (unit.isBlank()) continue
            val hit = try {
                withTimeoutOrNull(tierTimeoutMs) { load(unit) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null // fail open: a broken tier is just a miss
            }
            if (hit != null) return@withTimeoutOrNull hit
        }
        null
    }
}
