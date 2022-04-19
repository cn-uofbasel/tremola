package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.lifecycle.LiveData
import androidx.room.*

import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry

@Dao
interface LogEntryDAO {
    @Query("SELECT * FROM logentry ORDER BY tst DESC")
    fun getAll(): LiveData<List<LogEntry>>

    @Query("SELECT * FROM logentry")
    fun getAllAsList(): List<LogEntry>

    @Query("DELETE FROM logentry")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(logEntry: LogEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMultiple(vararg logEntry: LogEntry)

    @Delete
    fun delete(logEntry: LogEntry)

    @Query("SELECT * FROM logentry WHERE hid = :hashId")
    fun getEventByHashId(hashId: String): List<LogEntry>

    @Query("SELECT * FROM logentry WHERE lid = :logId AND lsq = :logSeq")
    fun getEventByLogIdAndSeq(logId: String, logSeq: Int): LogEntry?

    /*
    @Query("SELECT * FROM logentry WHERE cid = :convId ORDER BY clt DESC)
    fun getEventByConversationId(convId: String) : LiveData<List<LogEntry>>
    */

    @Query("SELECT * FROM logentry WHERE lid = :logId ORDER BY lsq DESC LIMIT 1")
    fun getMostRecentEventFromLogId(logId: String): LogEntry?

    @Query("DELETE FROM logentry")
    fun wipe()

}