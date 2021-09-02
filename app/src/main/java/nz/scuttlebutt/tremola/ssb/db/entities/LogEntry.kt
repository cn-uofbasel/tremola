package nz.scuttlebutt.tremola.ssb.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "LogEntry")
data class LogEntry( // as locally stored
    @PrimaryKey
    @ColumnInfo(name = "hid")  var hid: String,   // hash id (hash of raw entry), aka key
    @ColumnInfo(name = "lid")  var lid: String,   // log id, aka feed id, author
    @ColumnInfo(name = "lsq")  var lsq: Int,      // sequence number
    @ColumnInfo(name = "pre")  var pre: String?,  // previous
    @ColumnInfo(name = "tst")  var tst: Long,     // timestamp
    @ColumnInfo(name = "cid")  var cid: String?,  // conversation id
    @ColumnInfo(name = "clt")  var clt: String?,  // conversation logical time
    @ColumnInfo(name = "pub")  var pub: String?,  // public content/conversation
    @ColumnInfo(name = "pri")  var pri: String?,  // private content/conversation
    @ColumnInfo(name = "raw", typeAffinity = ColumnInfo.BLOB) var raw: ByteArray // SSB wire format
)
