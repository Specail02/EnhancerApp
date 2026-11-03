package com.example.enhancer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.example.enhancer.ui.EnhancerTheme
import com.example.enhancer.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // OpenDocument يعطي صلاحية دائمة للملف، فيستطيع التصدير في الخلفية قراءته لاحقًا
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(it); vm.load(it, MediaKind.IMAGE) }
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(it); vm.load(it, MediaKind.VIDEO) }
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // سواء وافق أم لا، التصدير يعمل؛ الإشعار فقط هو ما يتأثر
        vm.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnhancerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen(
                        vm = vm,
                        onPickImage = { pickImage.launch(arrayOf("image/*")) },
                        onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        onStart = ::startExport,
                        onOpen = ::openOutput
                    )
                }
            }
        }
    }

    private fun startExport() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.start()
        }
    }

    private fun persist(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun openOutput(uriString: String, kind: MediaKind) {
        val uri = Uri.parse(uriString)
        val type = contentResolver.getType(uri) ?: if (kind == MediaKind.VIDEO) "video/*" else "image/*"
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Throwable) {
        }
    }
}
