package nz.scuttlebutt.tremola.ssb.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Contact")
data class Contact(
    @PrimaryKey
    @ColumnInfo(name = "lid")            val lid: String,
    @ColumnInfo(name = "alias")          val alias: String?,
    @ColumnInfo(name = "isPub")          val isPub: Boolean,
    @ColumnInfo(name = "pict")           val pict: String?,
    @ColumnInfo(name = "scan_low")       val scan_low: Int,
    @ColumnInfo(name = "front_sequence") val front_seq: Int,
    @ColumnInfo(name = "front_previous") val front_prev: String?
)