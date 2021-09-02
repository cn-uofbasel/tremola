package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.room.*
import nz.scuttlebutt.tremola.ssb.db.entities.Pub

@Dao
interface PubDAO {
    @Query("SELECT * FROM Pub")
    fun getAll(): List<Pub>

    @Query("DELETE FROM Pub")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(pub: Pub)

    @Delete
    fun delete(pub: Pub)

    @Query("DELETE FROM pub")
    fun wipe()
}