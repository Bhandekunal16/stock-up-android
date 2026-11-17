package com.example

import com.example.data.model.RatioValue
import com.example.data.remote.PortfolioJsonParser
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PortfolioJsonParserTest {

    @Test
    fun `parses date indexed portfolio snapshots`() {
        val raw = """
            {
              "2026-11-16": [
                {"company": "RELIANCE", "shares": 10, "currentValue": 13500.0, "referenceValue": 12000.0}
              ],
              "2026-11-17": {
                "stocks": [
                  {"company": "TCS", "shares": 5, "currentValue": 20000.0, "referenceValue": 21000.0}
                ]
              }
            }
        """.trimIndent()

        val snapshots = PortfolioJsonParser.parseAllSnapshots(raw)
        assertEquals(2, snapshots.size)
        assertEquals("2026-11-16", snapshots[0].date)
        assertEquals(13500.0, snapshots[0].totalCurrentValue, 0.01)
        assertEquals("2026-11-17", snapshots[1].date)
        assertEquals(-1000.0, snapshots[1].netDifference, 0.01)
    }

    @Test
    fun `parses single date snapshot as array or object`() {
        val arrayPayload = """
            {"data": [{"company": "INFY", "shares": 12, "currentValue": 18000, "referenceValue": 15000}]}
        """.trimIndent()
        val snap1 = PortfolioJsonParser.parseSingleSnapshot("2026-11-17", arrayPayload)
        assertEquals(1, snap1.stocks.size)
        assertEquals("INFY", snap1.stocks[0].company)

        val objectPayload = """
            {"data": {"stocks": [{"company": "HDFCBANK", "shares": 8, "currentValue": 14000, "referenceValue": 13000}]}}
        """.trimIndent()
        val snap2 = PortfolioJsonParser.parseSingleSnapshot("2026-11-17", objectPayload)
        assertEquals(1, snap2.stocks.size)
        assertEquals("HDFCBANK", snap2.stocks[0].company)
    }

    @Test
    fun `parses screener snapshot with dynamic ratios including range and null`() {
        val raw = """
            {
              "success": true,
              "companyCode": "RELIANCE",
              "data": {
                "time": "2026-11-17T08:30:51+00:00",
                "company": "Reliance Industries Ltd",
                "price": 1363,
                "marketCap": 1844823,
                "stockPE": 22.5,
                "high": 1612,
                "low": 1152,
                "changed": true,
                "ratios": {
                  "Current Price": 1363,
                  "High / Low": [1612, 1152],
                  "ROCE": 10.3,
                  "Dividend Yield": null,
                  "Sector": "Energy"
                }
              }
            }
        """.trimIndent()

        val rec = PortfolioJsonParser.parseScreenerSnapshotResponse(raw, "RELIANCE")
        assertEquals("RELIANCE", rec.companyCode)
        assertEquals("Reliance Industries Ltd", rec.companyName)
        assertEquals(1363.0, rec.price!!, 0.01)
        assertTrue(rec.changed)
        assertEquals(RatioValue.NumberVal(1363.0), rec.ratios["Current Price"])
        assertEquals(RatioValue.RangeVal(1612.0, 1152.0), rec.ratios["High / Low"])
        assertEquals(RatioValue.NullVal, rec.ratios["Dividend Yield"])
        assertEquals(RatioValue.TextVal("Energy"), rec.ratios["Sector"])
    }

    @Test
    fun `parses signals success and error responses`() {
        val ok = PortfolioJsonParser.parseSignalsResponse(
            """{"success": true, "exitCode": 0, "output": "BUY RELIANCE", "error": ""}""",
            200,
        )
        assertTrue(ok.success)
        assertEquals(0, ok.exitCode)
        assertEquals("BUY RELIANCE", ok.output)

        val fail = PortfolioJsonParser.parseSignalsResponse(
            """{"success": false, "message": "Timeout"}""",
            504,
        )
        assertFalse(fail.success)
        assertNull(fail.exitCode)
        assertEquals("Timeout", fail.error)
    }

    @Test
    fun `formats Indian currency properly`() {
        assertEquals("₹1,23,456.78", Formatters.formatInr(123456.78))
        assertEquals("-₹1,23,456.78", Formatters.formatInr(-123456.78))
        assertEquals("+₹12,500.00", Formatters.formatSignedInr(12500.0))
    }
}
