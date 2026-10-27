package com.datapulse.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Export sheet (Table 7): scoped cache, per-share URI grant, no storage permission, ever.
@Singleton
class Exporter @Inject constructor(@ApplicationContext private val ctx: Context, private val repo: PulseRepository) {
    suspend fun shareDaily(): Unit = share("daily.csv", dailyCsv())
    suspend fun shareApps(from: String): Unit = share("apps.csv", appsCsv(from))
    suspend fun shareHourly(from: String): Unit = share("hourly.csv", hourlyCsv(from))

    private suspend fun dailyCsv(): String = withContext(Dispatchers.IO) {
        val rows = repo.recentDaysList(365)
        buildString {
            appendLine("date,total_mib,wifi_mib,mobile_mib,unattributed_mib,override_mib")
            for (r in rows.reversed())
                appendLine("${r.date},${r.totalMbUsed},${r.wifiMbUsed},${r.mobileMbUsed},${r.unattributedMb},${r.overrideMb ?: ""}")
        }
    }

    private suspend fun appsCsv(from: String): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("date,package,app,mobile_mib,wifi_mib,fg_mib,bg_mib,rx_bytes,tx_bytes")
            for (a in repo.appsList(from))
                appendLine("${a.date},${csvCell(a.packageName)},${csvCell(a.appName)},${a.mobileMb},${a.wifiMb},${a.fgMb},${a.bgMb},${a.rxBytes},${a.txBytes}")
        }
    }

    private suspend fun hourlyCsv(from: String): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("date,hour,mobile_mib,wifi_mib")
            for (h in repo.hoursList(from)) appendLine("${h.date},${h.hour},${h.mobileMb},${h.wifiMb}")
        }
    }

    private fun share(name: String, csv: String) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, "${System.currentTimeMillis()}-$name").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(send, "Share DataPulse report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
