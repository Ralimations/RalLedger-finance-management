package com.ralledger.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyFormattingInstrumentedTest {
    @Test
    fun currencyFormattingKeepsCentavosBeyondDoubleExactIntegerRange() {
        val amount = Money(9_007_199_254_740_993L)

        assertTrue(amount.format("PHP").endsWith("409.93"))
    }
}
