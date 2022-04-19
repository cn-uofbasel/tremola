package nz.scuttlebutt.tremola.ssb.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "LogEntry")
data class LogEntry( // as locally stored
    /** hash id (hash of raw entry), aka key */
    @PrimaryKey
    @ColumnInfo(name = "hid")  var hid: String,

    /** log id, aka feed id, author */
    @ColumnInfo(name = "lid")  var lid: String,

    /** sequence number */
    @ColumnInfo(name = "lsq")  var lsq: Int,

    /** previous */
    @ColumnInfo(name = "pre")  var pre: String?,

    /** timestamp*/
    @ColumnInfo(name = "tst")  var tst: Long,

    /** conversation id */
    @ColumnInfo(name = "cid")  var cid: String?,

    /** conversation logical time */
    @ColumnInfo(name = "clt")  var clt: String?,

    /** public content/conversation */
    @ColumnInfo(name = "pub")  var pub: String?,

    /** private content/conversation */
    @ColumnInfo(name = "pri")  var pri: String?,

    /** SSB wire format */
    @ColumnInfo(name = "raw", typeAffinity = ColumnInfo.BLOB) var raw: ByteArray
)
