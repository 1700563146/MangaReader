package io.github.saonte.mangareader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.saonte.mangareader.domain.InstallResult
import io.github.saonte.mangareader.domain.SourceManager
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 仅 Debug 构建存在：安装指向本机开发服（10.0.2.2:8765）的测试规则。
 * Release 包不包含该 Activity、assets/dev_rule.json 或任何内置源。
 *
 * 用法：adb shell am start -n <applicationId>/io.github.saonte.mangareader.DevInstallActivity
 */
@AndroidEntryPoint
class DevInstallActivity : ComponentActivity() {

    @Inject
    lateinit var sourceManager: SourceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val raw = assets.open("dev_rule.json").bufferedReader().readText()
                .replace("https://example.com", DEV_BASE)
                .replace("\"hosts\": [\"example.com\"]", "\"hosts\": [\"10.0.2.2\"]")
                .replace("\"allowCleartext\": false", "\"allowCleartext\": true")
            when (val result = sourceManager.install(raw, origin = "debug:devserver")) {
                is InstallResult.Ok -> Log.i(TAG, "installed dev rule: ${result.name} (${result.type})")
                is InstallResult.Invalid -> Log.e(
                    TAG,
                    "dev rule invalid: " + result.issues.joinToString { "${it.pointer}: ${it.message}" },
                )
            }
            finish()
        }
    }

    private companion object {
        const val TAG = "DevInstall"
        const val DEV_BASE = "http://10.0.2.2:8765"
    }
}
