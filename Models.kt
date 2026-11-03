package com.example.enhancer

import android.graphics.Bitmap
import android.net.Uri
import androidx.work.Data

enum class MediaKind { IMAGE, VIDEO }

/** الدقة المستهدفة — تُطبَّق على الضلع الأقصر فيحافظ التطبيق على أي إطار (طولي/عرضي/مربع). */
enum class ResolutionPreset(val label: String, val shortSide: Int?) {
    ORIGINAL("الأصلية", null),
    HD("HD 720p", 720),
    FHD("Full HD 1080p", 1080),
    QHD("2K 1440p", 1440),
    UHD("4K 2160p", 2160)
}

enum class QualityLevel(
    val label: String,
    val bitrateFactor: Double,
    val jpegQuality: Int,
    val bytesPerPixel: Double
) {
    STANDARD("قياسية", 0.7, 88, 0.20),
    HIGH("عالية", 1.0, 94, 0.32),
    MAX("قصوى", 1.5, 98, 0.55)
}

data class EnhanceSettings(
    val resolution: ResolutionPreset = ResolutionPreset.FHD,
    val quality: QualityLevel = QualityLevel.HIGH,
    val sharpness: Float = 0.5f,          // 0..1
    val cinematic: Boolean = true,
    val cinematicStrength: Float = 0.6f,  // 0..1
    val useHevc: Boolean = true           // فيديو فقط: حجم أصغر بنفس الجودة
)

data class MediaInfo(
    val uri: Uri,
    val kind: MediaKind,
    val width: Int,          // بعد تطبيق التدوير (الأبعاد كما تُعرض)
    val height: Int,
    val durationMs: Long,
    val fps: Float,
    val name: String,
    val fileSize: Long,
    val thumbnail: Bitmap?
)

fun EnhanceSettings.putInto(b: Data.Builder): Data.Builder = b
    .putString("res", resolution.name)
    .putString("quality", quality.name)
    .putFloat("sharp", sharpness)
    .putBoolean("cine", cinematic)
    .putFloat("cineStr", cinematicStrength)
    .putBoolean("hevc", useHevc)

fun Data.toSettings(): EnhanceSettings = EnhanceSettings(
    resolution = ResolutionPreset.valueOf(getString("res") ?: ResolutionPreset.FHD.name),
    quality = QualityLevel.valueOf(getString("quality") ?: QualityLevel.HIGH.name),
    sharpness = getFloat("sharp", 0.5f),
    cinematic = getBoolean("cine", true),
    cinematicStrength = getFloat("cineStr", 0.6f),
    useHevc = getBoolean("hevc", true)
)
