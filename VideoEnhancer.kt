@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.enhancer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * تحسين الفيديو عبر Media3 Transformer (ترميز بالعتاد):
 * تغيير الدقة (يحافظ على الإطار) ← حدة على GPU ← LUT سينمائي ← H.265/H.264 بمعدل بت محسوب.
 * يعمل على أي نسبة أبعاد (طولي/عرضي/مربع).
 */
object VideoEnhancer {

    suspend fun run(
        context: Context,
        info: MediaInfo,
        s: EnhanceSettings,
        plan: Plan,
        out: File,
        onProgress: (Int) -> Unit
    ) {
        // Transformer يجب أن يُستخدم على خيط له Looper
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val handler = Handler(Looper.getMainLooper())

                val effects = mutableListOf<Effect>()
                if (plan.outW != info.width || plan.outH != info.height) {
                    effects += Presentation.createForWidthAndHeight(
                        plan.outW, plan.outH, Presentation.LAYOUT_SCALE_TO_FIT
                    )
                }
                if (s.sharpness > 0.01f) effects += SharpenEffect(s.sharpness * 1.2f)
                if (s.cinematic) {
                    effects += SingleColorLut.createFromCube(CinematicGrade.buildCube(33, s.cinematicStrength))
                }

                val edited = EditedMediaItem.Builder(MediaItem.fromUri(info.uri))
                    .setEffects(Effects(emptyList(), effects))
                    .build()

                val composition = Composition.Builder(EditedMediaItemSequence.Builder(edited).build())
                    // فيديو HDR → SDR حتى يعمل التدرّج اللوني بشكل صحيح
                    .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build()
                    )
                    .setEnableFallback(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(if (s.useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacksAndMessages(null)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            handler.removeCallbacksAndMessages(null)
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                        handler.postDelayed(this, 500)
                    }
                }

                cont.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacksAndMessages(null)
                        transformer.cancel()
                    }
                }

                try {
                    transformer.start(composition, out.absolutePath)
                    handler.post(poll)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }
        }
    }
}
