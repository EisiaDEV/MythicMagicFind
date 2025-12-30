package com.eisiadev.enceladus.pouches.base

import kotlin.math.pow

sealed class TierConfig {
    data class Exponential(val minTier: Int, val maxTier: Int, val base: Double = 10.0) : TierConfig() {
        fun getTierValue(tier: Int): Long = 
            base.pow((tier - 1).toDouble()).toLong()
    }
    
    data class Custom(
        val tierMapping: Map<String, Int>,
        val conversionRates: List<Long>
    ) : TierConfig() {
        fun getTierByItemName(itemName: String): Int? = tierMapping[itemName]
        fun getTierValue(tier: Int): Long = 
            if (tier <= conversionRates.size) conversionRates[tier - 1] else 0L
    }
}