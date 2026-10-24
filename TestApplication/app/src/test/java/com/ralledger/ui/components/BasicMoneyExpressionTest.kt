package com.ralledger.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class BasicMoneyExpressionTest {
    @Test
    fun respectsOperatorPrecedenceAndParentheses() {
        assertEquals(BigDecimal("14"), BasicMoneyExpression.evaluate("2+3*4"))
        assertEquals(BigDecimal("20"), BasicMoneyExpression.evaluate("(2+3)*4"))
    }

    @Test
    fun supportsDecimalsUnarySignsAndCalculatorGlyphs() {
        assertEquals(BigDecimal("-6.5"), BasicMoneyExpression.evaluate("−2.5 × 2 + (0.5 − 2)"))
    }

    @Test
    fun divisionRoundsToCurrencyPrecisionWhenUsed() {
        val rounded = BasicMoneyExpression.evaluate("10/3")!!.setScale(2, RoundingMode.HALF_UP)
        assertEquals(BigDecimal("3.33"), rounded)
    }

    @Test
    fun rejectsIncompleteAndUnsafeExpressions() {
        assertNull(BasicMoneyExpression.evaluate("2+"))
        assertNull(BasicMoneyExpression.evaluate("1/0"))
        assertNull(BasicMoneyExpression.evaluate("1+system"))
    }
}
