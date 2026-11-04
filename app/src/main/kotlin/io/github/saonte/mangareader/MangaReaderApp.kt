package io.github.saonte.mangareader

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.saonte.mangareader.data.db.MangaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MangaReaderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var database: MangaDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // 调试期主动打开一次数据库，尽早暴露迁移/索引不一致问题。
            appScope.launch {
                runCatching { database.appMetaDao().get("install_id") }
                    .onFailure { Log.e(TAG, "database open failed", it) }
                    .onSuccess { Log.i(TAG, "database open ok") }
            }
        }
    }

    private companion object {
        const val TAG = "MangaReader"
    }
}
