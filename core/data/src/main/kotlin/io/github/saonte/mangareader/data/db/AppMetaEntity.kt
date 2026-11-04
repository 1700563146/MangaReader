package io.github.saonte.mangareader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 小型键值元数据（last backup、install id 等），不放业务实体。
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String?,
)
