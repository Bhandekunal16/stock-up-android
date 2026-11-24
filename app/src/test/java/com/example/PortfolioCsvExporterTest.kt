package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.StockItem
import com.example.ui.components.PortfolioExportDialog
import com.example.ui.theme.StockFolioTheme
import com.example.util.CsvExportPayload
import com.example.util.CsvExportScope
import com.example.util.PortfolioCsvExporter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PortfolioCsvExporterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleSnapshots = listOf(
        PortfolioSnapshot(
            date = "2026-11-24",
            stocks = listOf(
                StockItem("TCS", shares = 10.0, currentValue = 42000.0, referenceValue = 38000.0),
                StockItem("Infosys, Ltd.", shares = 15.5, currentValue = 28000.0, referenceValue = 30000.0),
            ),
        ),
        PortfolioSnapshot(
            date = "2026-11-20",
            stocks = listOf(
                StockItem("TCS", shares = 10.0, currentValue = 40000.0, referenceValue = 38000.0),
            ),
        ),
    )

    @Test
    fun `latest holdings payload formats headers and sorted rows accurately`() {
        val payload = PortfolioCsvExporter.buildCsvPayload(
            snapshots = sampleSnapshots,
            scope = CsvExportScope.LATEST_HOLDINGS,
        )

        assertEquals("stockfolio_holdings_2026-11-24.csv", payload.fileName)
        assertEquals(2, payload.rowCount)

        val lines = payload.content.trim().lines()
        assertEquals(3, lines.size)
        assertEquals(
            "Snapshot Date,Company,Shares,Current Value (INR),Reference Value (INR),Avg Value Per Share (INR),Net P&L (INR),Return %,Weight %",
            lines[0],
        )
        // Highest value first (TCS = 42000 / 70000 = 60.00%)
        assertEquals("2026-11-24,TCS,10,42000.00,38000.00,4200.00,4000.00,10.53,60.00", lines[1])
        // Comma in company name is RFC 4180 quoted
        assertTrue(lines[2].startsWith("2026-11-24,\"Infosys, Ltd.\",15.5,28000.00,30000.00,"))
    }

    @Test
    fun `all history holdings and daily summary payloads cover chronological snapshots`() {
        val allHoldings = PortfolioCsvExporter.buildCsvPayload(
            snapshots = sampleSnapshots,
            scope = CsvExportScope.ALL_HISTORY_HOLDINGS,
        )
        assertEquals("stockfolio_all_holdings_2026-11-24.csv", allHoldings.fileName)
        assertEquals(3, allHoldings.rowCount)
        val allLines = allHoldings.content.trim().lines()
        assertTrue(allLines[1].startsWith("2026-11-20,TCS"))
        assertTrue(allLines[2].startsWith("2026-11-24,TCS"))

        val summary = PortfolioCsvExporter.buildCsvPayload(
            snapshots = sampleSnapshots,
            scope = CsvExportScope.SNAPSHOT_SUMMARY,
        )
        assertEquals("stockfolio_summary_2026-11-24.csv", summary.fileName)
        assertEquals(2, summary.rowCount)
        val summaryLines = summary.content.trim().lines()
        assertEquals(
            "Snapshot Date,Holdings Count,Total Current Value (INR),Total Reference Value (INR),Net P&L (INR),Return %",
            summaryLines[0],
        )
        assertEquals("2026-11-20,1,40000.00,38000.00,2000.00,5.26", summaryLines[1])
        assertEquals("2026-11-24,2,70000.00,68000.00,2000.00,2.94", summaryLines[2])
    }

    @Test
    fun `single snapshot scope targets selected date`() {
        val target = sampleSnapshots[1]
        val payload = PortfolioCsvExporter.buildCsvPayload(
            snapshots = sampleSnapshots,
            scope = CsvExportScope.SINGLE_SNAPSHOT,
            targetSnapshot = target,
        )
        assertEquals("stockfolio_snapshot_2026-11-20.csv", payload.fileName)
        assertEquals(1, payload.rowCount)
        assertTrue(payload.content.contains("2026-11-20,TCS,10,40000.00"))
    }

    @Test
    fun `rfc4180 escaping and formula injection sanitization work cleanly`() {
        assertEquals("\"Acme \"\"Corp\"\"\"", PortfolioCsvExporter.escapeCsvField("Acme \"Corp\""))
        assertEquals("'=1+1", PortfolioCsvExporter.sanitizeTextCell("=1+1"))
        assertEquals("'+cmd", PortfolioCsvExporter.sanitizeTextCell("+cmd"))
        assertEquals("'@SUM", PortfolioCsvExporter.sanitizeTextCell("@SUM"))
        assertEquals("Reliance Industries", PortfolioCsvExporter.sanitizeTextCell("Reliance\nIndustries"))
    }

    @Test
    fun `writeCsvToUri writes UTF-8 CSV content to destination uri`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val tempFile = File(context.cacheDir, "test_export.csv")
            if (tempFile.exists()) tempFile.delete()
            val payload = PortfolioCsvExporter.buildCsvPayload(sampleSnapshots, CsvExportScope.LATEST_HOLDINGS)

            val result = PortfolioCsvExporter.writeCsvToUri(context, Uri.fromFile(tempFile), payload.content)
            assertTrue(result.isSuccess)
            assertTrue(tempFile.exists())
            val written = tempFile.readText(Charsets.UTF_8)
            assertEquals(payload.content, written)
            tempFile.delete()
        }
    }

    @Test
    fun `writeCsvToShareFile creates fileprovider uri and cleans stale exports`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val staleFile = File(exportDir, "old_export.csv").apply { writeText("old") }

            val payload = PortfolioCsvExporter.buildCsvPayload(sampleSnapshots, CsvExportScope.SNAPSHOT_SUMMARY)
            val result = PortfolioCsvExporter.writeCsvToShareFile(context, payload.fileName, payload.content)

            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
            assertFalse(staleFile.exists())
            val created = File(exportDir, payload.fileName)
            assertTrue(created.exists())
            assertEquals(payload.content, created.readText(Charsets.UTF_8))
        }
    }

    @Test
    fun `export dialog updates scope preview and triggers save callback`() {
        var savedPayload: CsvExportPayload? = null

        composeRule.setContent {
            StockFolioTheme {
                PortfolioExportDialog(
                    snapshots = sampleSnapshots,
                    initialScope = CsvExportScope.LATEST_HOLDINGS,
                    onSaveToDevice = { savedPayload = it },
                    onShareCsv = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Export Portfolio CSV").assertIsDisplayed()
        composeRule.onNodeWithText("stockfolio_holdings_2026-11-24.csv")
            .performScrollTo()
            .assertIsDisplayed()

        // Switch to Daily Snapshot Summary
        composeRule.onNodeWithTag("export_scope_snapshot_summary")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("stockfolio_summary_2026-11-24.csv")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("export_save_csv_button")
            .performScrollTo()
            .performClick()
        assertNotNull(savedPayload)
        assertEquals(CsvExportScope.SNAPSHOT_SUMMARY, savedPayload?.scope)
        assertEquals(2, savedPayload?.rowCount)
    }
}
