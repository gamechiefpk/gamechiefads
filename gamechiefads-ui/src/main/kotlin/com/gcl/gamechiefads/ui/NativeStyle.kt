package com.gcl.gamechiefads.ui

import androidx.annotation.ColorInt

/**
 * Optional look of a native ad. Every colour left null follows the built-in light/dark defaults
 * (res/values/colors.xml, override those names in your app to retheme everything at once).
 */
class NativeStyle private constructor(
    @param:ColorInt internal val background: Int?,
    @param:ColorInt internal val stroke: Int?,
    @param:ColorInt internal val headline: Int?,
    @param:ColorInt internal val body: Int?,
    @param:ColorInt internal val secondary: Int?,
    @param:ColorInt internal val badgeBackground: Int?,
    @param:ColorInt internal val badgeText: Int?,
    @param:ColorInt internal val ctaBackground: Int?,
    @param:ColorInt internal val ctaText: Int?,
    internal val cornerRadiusDp: Float?,
    internal val strokeWidthDp: Float?,
) {
    class Builder internal constructor() {
        private var background: Int? = null
        private var stroke: Int? = null
        private var headline: Int? = null
        private var body: Int? = null
        private var secondary: Int? = null
        private var badgeBackground: Int? = null
        private var badgeText: Int? = null
        private var ctaBackground: Int? = null
        private var ctaText: Int? = null
        private var cornerRadiusDp: Float? = null
        private var strokeWidthDp: Float? = null

        fun background(@ColorInt c: Int) = apply { background = c }
        fun stroke(@ColorInt c: Int) = apply { stroke = c }
        fun headlineColor(@ColorInt c: Int) = apply { headline = c }
        fun bodyColor(@ColorInt c: Int) = apply { body = c }
        fun secondaryColor(@ColorInt c: Int) = apply { secondary = c }
        fun badge(@ColorInt background: Int, @ColorInt text: Int) = apply { badgeBackground = background; badgeText = text }
        fun button(@ColorInt background: Int, @ColorInt text: Int) = apply { ctaBackground = background; ctaText = text }
        fun cornerRadiusDp(dp: Float) = apply { cornerRadiusDp = dp.coerceAtLeast(0f) }
        fun strokeWidthDp(dp: Float) = apply { strokeWidthDp = dp.coerceAtLeast(0f) }

        fun build() = NativeStyle(
            background, stroke, headline, body, secondary, badgeBackground, badgeText,
            ctaBackground, ctaText, cornerRadiusDp, strokeWidthDp,
        )
    }

    companion object {
        @JvmStatic fun builder() = Builder()

        /** Built-in look (follows light/dark). */
        @JvmField val DEFAULT = Builder().build()
    }
}
