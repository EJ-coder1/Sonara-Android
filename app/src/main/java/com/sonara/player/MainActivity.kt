package com.sonara.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.SonaraRoot
import com.sonara.player.ui.theme.SonaraTheme
import com.sonara.player.ui.theme.isDarkFor
import java.io.File

class MainActivity : ComponentActivity() {

    private val crashFile: File get() = File(filesDir, "last_crash.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        installCrashLogger()
        enableEdgeToEdge()

        val container = (application as SonaraApp).container

        setContent {
            val appVm: AppViewModel = viewModel()
            val mode by appVm.themeMode.collectAsStateWithLifecycle()
            val dynamic by appVm.dynamicColor.collectAsStateWithLifecycle()
            val uiPrefs by appVm.uiPrefs.collectAsStateWithLifecycle()
            val onboardingDone by appVm.onboardingDone.collectAsStateWithLifecycle()
            val dark = isDarkFor(mode)

            // Keep the splash screen up until the saved settings are read (no welcome-screen flash).
            splash.setKeepOnScreenCondition { onboardingDone == null }

            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1B, 0x1B, 0x1B),
                    ) { dark },
                )
                onDispose { }
            }

            SonaraTheme(darkTheme = dark, dynamicColor = dynamic, amoled = uiPrefs.amoled, accent = uiPrefs.accent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SonaraRoot(appVm = appVm, player = container.player)
                    CrashReportDialog(crashFile)
                }
            }
        }
    }

    /** Debug builds only: saves the crash details so the next launch can show them. */
    private fun installCrashLogger() {
        if (!BuildConfig.DEBUG) return
        val file = crashFile
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file.writeText("Thread: " + thread.name + "\n" + Log.getStackTraceString(error))
            } catch (e: Throwable) {
                // Nothing more can be done while the app is already crashing.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** Debug builds only: after a crash, shows what went wrong so it can be copied and sent. */
@Composable
private fun CrashReportDialog(file: File) {
    var report by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG && file.exists()) {
                try {
                    file.readText()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        )
    }
    val text = report ?: return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            file.delete()
            report = null
        },
        title = { Text("Sonara crashed last time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = text.take(3000),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Sonara crash", text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = {
                file.delete()
                report = null
            }) { Text("Close") }
        },
    )
}
