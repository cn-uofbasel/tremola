package nz.scuttlebutt.tremola.ssb.db.entities

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Blob")
data class Blob(
    @PrimaryKey
    @ColumnInfo(name = "ref") val id: String,
    @ColumnInfo(name = "distance") val distance: Int,
    @ColumnInfo(name = "size") val size: Long,
    // @ColumnInfo(name = "location") val location: Uri
)