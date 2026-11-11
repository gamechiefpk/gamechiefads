package com.gcl.gamechiefads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tries ordered unit ids; returns the first non-null result, or null.
 * Each tier has its own timeout and the whole run has an overall budget. Errors count as a miss.
 * The loader must release whatever it loaded if it is cancelled (use invokeOnCancellation).
 */
internal object Waterfall {

    /** Simple form: sequential, one timeout for every tier. */
    suspend fun <T : Any> run(
        units: List<String>,
        tierTimeoutMs: Long,
        overallBudgetMs: Long,
        load: suspend (unitId: String) -> T?,
    ): T? = run(units, { tierTimeoutMs }, overallBudgetMs, WaterfallMode.SEQUENTIAL, {}, { _, _ -> }, load)

    /**
     * Full form. [timeoutFor] gives each unit its own timeout; [mode] picks one-by-one or all-at-once;
     * [discard] frees an ad that arrived but lost the race (parallel); [onTier] is told every tier that
     * finished (hit or miss, not tiers cut short by the overall budget).
     */
    suspend fun <T : Any> run(
        units: List<String>,
        timeoutFor: (String) -> Long,
        overallBudgetMs: Long,
        mode: WaterfallMode,
        discard: (T) -> Unit,
        onTier: (unit: String, hit: Boolean) -> Unit,
        load: suspend (unitId: String) -> T?,
    ): T? {
        val valid = units.filter { it.isNotBlank() }
        if (valid.isEmpty()) return null

        suspend fun tier(unit: String): T? {
            val hit = try {
                withTimeoutOrNull(timeoutFor(unit)) { load(unit) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AdLog.d("waterfall") { "tier $unit threw: $e" }
                null // fail open: a broken tier is just a miss
            }
            if (hit == null) AdLog.d("waterfall") { "tier $unit: miss (${timeoutFor(unit)}ms cap)" }
            onTier(unit, hit != null)
            return hit
        }

        return withTimeoutOrNull(overallBudgetMs) {
            when (mode) {
                WaterfallMode.SEQUENTIAL -> {
                    for (unit in valid) tier(unit)?.let { return@withTimeoutOrNull it }
                    null
                }
                WaterfallMode.PARALLEL -> coroutineScope {
                    val winner = CompletableDeferred<T?>()
                    val pending = AtomicInteger(valid.size)
                    valid.forEach { unit ->
                        launch {
                            val hit = tier(unit)
                            if (hit != null && !winner.complete(hit)) discard(hit)
                            if (pending.decrementAndGet() == 0) winner.complete(null)
                        }
                    }
                    val result = winner.await()
                    coroutineContext.cancelChildren() // late tiers are cancelled; their ads are destroyed on cancel
                    result
                }
            }
        }
    }
}
