package com.eisiadev.enceladus.magicfind.util

import java.text.DecimalFormat

object DamageFormatter {
    
    private val VALUES = doubleArrayOf(1.0E28, 1.0E24, 1.0E20, 1.0E16, 1.0E12, 1.0E8, 1.0E4)
    private val UNITS = arrayOf("양", "자", "해", "경", "조", "억", "만")
    
    private val formatter = DecimalFormat("#,##0.##")
    
    /**
     * 데미지를 한국 숫자 단위로 포맷팅
     * 예: 123456789.5 -> "1.23억"
     */
    fun format(damage: Double): String {
        if (damage < 1.0E4) {
            return formatter.format(damage)
        }
        
        for (i in VALUES.indices) {
            if (damage >= VALUES[i]) {
                val value = damage / VALUES[i]
                val formattedValue = formatter.format(value)
                return formattedValue + UNITS[i]
            }
        }
        
        // fallback (거의 발생하지 않음)
        return formatter.format(damage / VALUES[0]) + UNITS[0]
    }
    
    /**
     * 기여도 퍼센트 포맷
     * 예: 0.12345 -> "12.35%"
     */
    fun formatContribution(ratio: Double): String {
        return String.format("%.2f%%", ratio * 100)
    }
    
    /**
     * Magic Find 포맷
     * 예: 123.456 -> "123.46%"
     */
    fun formatMagicFind(magicFind: Double): String {
        return String.format("%.2f%%", magicFind)
    }
}
