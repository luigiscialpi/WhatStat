package com.whatstats.domain.engagement

object CopyVariantPicker {
    fun pick(
        cardKey: String,
        daySeed: Long,
        variantCount: Int,
    ): Int {
        if (variantCount <= 1) return 0
        return "$cardKey|$daySeed".hashCode().mod(variantCount)
    }
}
