@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.enhancer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ينفّذ التحسين كخدمة أمامية (Foreground) مع إشعار تقدّم،
 * فيستمر التصدير حتى لو خرج المستخدم من التطبيق أو أغلق الشاشة.
 */
class ExportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        makeInfo(inputData.getString("name") ?: "", 0)

    override suspend fun doWork(): Result {
        val d = inputData
        val kind = MediaKind.valueOf(d.getString("kind") ?: return Result.failure())
        val info = MediaInfo(
            uri = Uri.parse(d.getString("uri") ?: return Result.failure()),
            kind = kind,
            width = d.getInt("w", 0),
            height = d.getInt("h", 0),
            durationMs = d.getLong("dur", 0L),
            fps = d.getFloat("fps", 30f),
            name = d.getString("name") ?: "media",
            fileSize = 0L,
            thumbnail = null
        )
        val s = d.toSettings()
        val plan = Planner.plan(info, s)
        val ext = if (kind == MediaKind.VIDEO) "mp4" else "jpg"
        val tmp = File(applicationContext.cacheDir, "export_$id.$ext")
        val progress = MutableStateFlow(0)

        return try {
            setForeground(makeInfo(info.name, 0))
            coroutineScope {
                val reporter = launch {
                    progress.collect { p ->
                        setProgress(workDataOf("p" to p))
                        setForeground(makeInfo(info.name, p))
                    }
                }
                try {
                    if (kind == MediaKind.VIDEO) {
                        VideoEnhancer.run(applicationContext, info, s, plan, tmp) { progress.value = it }
                    } else {
                        withContext(Dispatchers.Default) {
                            ImageEnhancer.run(applicationContext, info, s, plan, tmp) { progress.value = it }
                        }
                    }
                } finally {
                    reporter.cancel()
                }
            }

            val base = info.name.substringBeforeLast('.', info.name)
            val outName = "${base}_enhanced_${s.resolution.name}.$ext"
            val outUri = withContext(Dispatchers.IO) {
                save(tmp, outName, if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg", kind)
            }
            val size = tmp.length()
            tmp.delete()
            notifyDone(info.name, outUri, kind, success = true)
            Result.success(workDataOf("outUri" to outUri.toString(), "outBytes" to size))
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (t: Throwable) {
            tmp.delete()
            val msg = if (t is OutOfMemoryError) "الذاكرة غير كافية لهذه الدقة" else (t.message ?: t.javaClass.simpleName)
            notifyDone(info.name, null, kind, success = false)
            Result.failure(workDataOf("error" to msg))
        }
    }

    // ------------------------------------------------------------ الحفظ في المعرض

    private fun save(file: File, displayName: String, mime: String, kind: MediaKind): Uri {
        val resolver = applicationContext.contentResolver
        val collection = if (kind == MediaKind.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (kind == MediaKind.VIDEO) "Movies/Enhancer" else "Pictures/Enhancer"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("تعذّر إنشاء الملف في المعرض")
        try {
            resolver.openOutputStream(uri)!!.use { os -> file.inputStream().use { it.copyTo(os) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    // ------------------------------------------------------------ الإشعارات

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تصدير الوسائط", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun makeInfo(name: String, progress: Int): ForegroundInfo {
        ensureChannel()
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("جارٍ تحسين الجودة…")
            .setContentText("$name — $progress%")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", cancel)
            .build()
        return ForegroundInfo(NOTIF_ID_BASE + (id.hashCode() and 0xFFFF), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notifyDone(name: String, uri: Uri?, kind: MediaKind, success: Boolean) {
        ensureChannel()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (success) "اكتمل التحسين ✓" else "فشل التحسين")
            .setContentText(name)
            .setAutoCancel(true)
        if (success && uri != null) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, if (kind == MediaKind.VIDEO) "video/*" else "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    applicationContext, id.hashCode(), view,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            builder.setContentIntent(openAppIntent())
        }
        nm.notify(NOTIF_ID_BASE + 0x10000 + (id.hashCode() and 0xFFFF), builder.build())
    }

    private companion object {
        const val CHANNEL = "export"
        const val NOTIF_ID_BASE = 4000
    }
}
