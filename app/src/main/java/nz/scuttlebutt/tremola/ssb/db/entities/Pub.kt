package nz.scuttlebutt.tremola.ssb.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Pub")
data class Pub(
    @PrimaryKey
    @ColumnInfo(name = "lid") val lid: String,
    @ColumnInfo(name = "host") val host: String,
    @ColumnInfo(name = "port") val port: Int
)