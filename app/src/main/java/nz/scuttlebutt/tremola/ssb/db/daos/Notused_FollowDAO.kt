package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.room.*
import nz.scuttlebutt.tremola.ssb.db.entities.Follow

@Dao
interface Notused_FollowDAO {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(follow: Follow)

    @Delete
    fun delete(follow: Follow)

    @Query("DELETE FROM Follow")
    fun deleteAll()

    @Query("DELETE FROM Follow")
    fun wipe()
}