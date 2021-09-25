package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import nz.scuttlebutt.tremola.ssb.db.entities.Blob

@Dao
interface BlobDAO {
    @Query("SELECT * FROM Blob")
    fun getAll(): List<Blob>

    @Query("SELECT * FROM Blob WHERE distance < 0")
    fun getWant(): List<Blob>

    @Query("SELECT * FROM Blob WHERE distance = 1")
    fun getHave(): List<Blob>

    @Query("DELETE FROM Blob")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(blob: Blob)

    @Delete
    fun delete(blob: Blob)

    @Query("DELETE FROM Blob")
    fun wipe()
}