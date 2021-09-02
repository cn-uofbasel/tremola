package nz.scuttlebutt.tremola.ssb.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "Follow", primaryKeys = ["who","whom"])
data class Follow(
    @ColumnInfo(name = "who") val who: Int,
    @ColumnInfo(name = "whom") val whom: Int,
    @ColumnInfo(name = "state") val state: Int
)