package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import nz.scuttlebutt.tremola.ssb.db.entities.Blob

@Dao
interface Notused_BlobDAO {
    @Query("SELECT * FROM Blob")
    fun getAll(): LiveData<List<Blob>>

    @Query("DELETE FROM Blob")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(blob: Blob)

    @Delete
    fun delete(blob: Blob)

    @Query("DELETE FROM Blob")
    fun wipe()
}