package com.example.enhancer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.example.enhancer.EnhanceSettings
import com.example.enhancer.JobUi
import com.example.enhancer.MainViewModel
import com.example.enhancer.MediaInfo
import com.example.enhancer.MediaKind
import com.example.enhancer.Plan
import com.example.enhancer.Planner
import com.example.enhancer.QualityLevel
import com.example.enhancer.ResolutionPreset
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    vm: MainViewModel,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onStart: () -> Unit,
    onOpen: (String, MediaKind) -> Unit
) {
    val info = vm.selected
    val s = vm.settings
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val plan = remember(info, s) { info?.let { Planner.plan(it, s) } }
    val free = remember(plan) { vm.freeBytes() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("محسّن الجودة", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "ارفع دقة الصور والفيديو إلى 4K/HD بلمسة سينمائية",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                )
            }
        }

        if (info == null) {
            item { PickButtons(vm.loading, vm.loadError, onPickImage, onPickVideo) }
        } else {
            item { PreviewCard(info, onChange = vm::clearSelection) }
            item { SettingsCard(info, s, onChange = { vm.settings = it }) }
            item { if (plan != null) SummaryCard(info, plan, free) }
            item {
                val enough = plan == null || free >= plan.requiredFreeBytes
                Column {
                    Button(
                        onClick = onStart,
                        enabled = enough,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("ابدأ التحسين", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "التصدير يستمر في الخلفية حتى لو خرجت من التطبيق، وسيظهر إشعار بالتقدّم.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (jobs.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("التصدير", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearFinished) { Text("مسح المنتهية") }
                }
            }
            items(jobs, key = { it.id }) { job ->
                JobCard(job, onCancel = { vm.cancel(job.id) }, onOpen = { onOpen(it, job.kind) })
            }
        }
    }
}

// ------------------------------------------------------------------ اختيار الملف

@Composable
private fun PickButtons(loading: Boolean, error: String?, onImage: () -> Unit, onVideo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigPick("إضافة صورة", "أي حجم أو إطار — JPG / PNG / HEIC", onImage, !loading)
        BigPick("إضافة فيديو", "أي مدة أو نسبة أبعاد — طولي / عرضي / مربع", onVideo, !loading)
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.height(22.dp))
                Text("جارٍ قراءة الملف…")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BigPick(title: String, sub: String, onClick: () -> Unit, enabled: Boolean) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ المعاينة

@Composable
private fun PreviewCard(info: MediaInfo, onChange: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            info.thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(info.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val line = buildString {
                append("${info.width}×${info.height}")
                append(" • ${fmtMp(info.width.toDouble() * info.height / 1e6)}")
                if (info.kind == MediaKind.VIDEO) {
                    append(" • ${fmtDuration(info.durationMs)}")
                    append(" • ${info.fps.roundToInt()} إطار/ث")
                }
                if (info.fileSize > 0) append(" • ${fmtBytes(info.fileSize)}")
            }
            Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onChange, modifier = Modifier.padding(top = 8.dp)) { Text("تغيير الملف") }
        }
    }
}

// ------------------------------------------------------------------ الإعدادات

@Composable
private fun SettingsCard(info: MediaInfo, s: EnhanceSettings, onChange: (EnhanceSettings) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("الدقة (البكسل)")
            ChipRow {
                ResolutionPreset.values().forEach { r ->
                    FilterChip(selected = s.resolution == r, onClick = { onChange(s.copy(resolution = r)) }, label = { Text(r.label) })
                }
            }

            Label("جودة الإخراج")
            ChipRow {
                QualityLevel.values().forEach { q ->
                    FilterChip(selected = s.quality == q, onClick = { onChange(s.copy(quality = q)) }, label = { Text(q.label) })
                }
            }

            Label("الحدة: ${(s.sharpness * 100).roundToInt()}%")
            Slider(value = s.sharpness, onValueChange = { onChange(s.copy(sharpness = it)) })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("لمسة سينمائية", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = s.cinematic, onCheckedChange = { onChange(s.copy(cinematic = it)) })
            }
            if (s.cinematic) {
                Label("قوة التدرّج اللوني: ${(s.cinematicStrength * 100).roundToInt()}%")
                Slider(value = s.cinematicStrength, onValueChange = { onChange(s.copy(cinematicStrength = it)) })
            }

            if (info.kind == MediaKind.VIDEO) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ترميز HEVC (H.265)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "حجم أصغر بنحو 40% بنفس الجودة",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = s.useHevc, onCheckedChange = { onChange(s.copy(useHevc = it)) })
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ------------------------------------------------------------------ الملخّص والمساحة

@Composable
private fun SummaryCard(info: MediaInfo, plan: Plan, free: Long) {
    val enough = free >= plan.requiredFreeBytes
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("النتيجة المتوقعة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("الأبعاد: ${plan.outW}×${plan.outH}  (${fmtMp(plan.megapixels)})")
            Text("الحجم التقريبي: ${fmtBytes(plan.estimatedBytes)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (info.kind == MediaKind.VIDEO) {
                Text(
                    "معدل البت: ${String.format(Locale.US, "%.1f", plan.videoBitrate / 1e6)} Mbps • المدة ${fmtDuration(info.durationMs)}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "المساحة الحرة: ${fmtBytes(free)}",
                fontSize = 13.sp,
                color = if (enough) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
            if (!enough) {
                Text(
                    "المساحة غير كافية (المطلوب ≈ ${fmtBytes(plan.requiredFreeBytes)}). خفّض الدقة أو الجودة.",
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                )
            }
            plan.notes.forEach {
                Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ المهام

@Composable
private fun JobCard(job: JobUi, onCancel: () -> Unit, onOpen: (String) -> Unit) {
    val running = job.state == WorkInfo.State.RUNNING
    val waiting = job.state == WorkInfo.State.ENQUEUED || job.state == WorkInfo.State.BLOCKED
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (if (job.kind == MediaKind.VIDEO) "🎬 " else "🖼️ ") + job.name,
                fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            when {
                running -> {
                    LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("جارٍ التحسين… ${job.progress}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                waiting -> Text("في الانتظار…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                job.state == WorkInfo.State.SUCCEEDED ->
                    Text(
                        "اكتمل ✓ — الحجم ${fmtBytes(job.outBytes)} • محفوظ في المعرض (Enhancer)",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary
                    )
                job.state == WorkInfo.State.CANCELLED ->
                    Text("أُلغي", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text("فشل: ${job.error ?: "خطأ غير معروف"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running || waiting) OutlinedButton(onClick = onCancel) { Text("إلغاء") }
                if (job.state == WorkInfo.State.SUCCEEDED && job.outUri != null) {
                    Button(onClick = { onOpen(job.outUri) }) { Text("عرض") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ أدوات التنسيق

private fun fmtBytes(b: Long): String {
    val mb = b / 1_048_576.0
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024) else String.format(Locale.US, "%.1f MB", mb)
}

private fun fmtMp(mp: Double) = String.format(Locale.US, "%.1f MP", mp)

private fun fmtDuration(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
