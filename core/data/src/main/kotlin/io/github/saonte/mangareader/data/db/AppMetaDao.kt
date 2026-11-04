package io.github.saonte.mangareader.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetaDao {

    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE `key` = :key")
    suspend fun remove(key: String)
}
