package com.gcl.gamechiefads

/** How the ordered ad units ("floors": high, medium, normal...) of a placement are tried. */
enum class WaterfallMode {
    /** One unit at a time, in order, each with its own timeout. First hit wins. Cheapest on requests (default). */
    SEQUENTIAL,

    /** All units at once; the first ad to arrive wins, the others are dropped. Fastest, but sends one request per unit. */
    PARALLEL,
}
