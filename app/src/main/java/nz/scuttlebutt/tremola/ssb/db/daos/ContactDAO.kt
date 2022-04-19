package nz.scuttlebutt.tremola.ssb.db.daos

import androidx.lifecycle.LiveData
import androidx.room.*

import nz.scuttlebutt.tremola.ssb.db.entities.Contact

@Dao
interface ContactDAO {
    @Query("SELECT * FROM contact")
    fun getAll(): List<Contact>

    /**
     * Retrieve a contact by its log id (i.e. public id)
     */
    @Query("SELECT * FROM contact WHERE lid = :lid")
    fun getContactByLid(lid: String): Contact?

    @Query("DELETE FROM contact")
    fun deleteAll()

    @Query("SELECT * FROM contact INNER JOIN Follow ON contact.lid = Follow.who AND Follow.state > 0")
    fun getFollowing(): LiveData<List<Contact>>

    @Query("SELECT * FROM contact INNER JOIN Follow ON contact.lid = Follow.whom AND Follow.state > 0")
    fun getFollowers(): LiveData<List<Contact>>

    @Insert
    fun insertContact(vararg peers: Contact)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertContact(peer: Contact): Long

    @Update
    fun updateContact(vararg peers: Contact)

    @Query("DELETE FROM contact")
    fun wipe()

}