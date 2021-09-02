package nz.scuttlebutt.tremola.ssb.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import nz.scuttlebutt.tremola.ssb.db.daos.*
import nz.scuttlebutt.tremola.ssb.db.entities.*
import nz.scuttlebutt.tremola.utils.SingletonHolder

@Database(
    entities = [Contact::class, LogEntry::class, Pub::class,
                Blob::class, Follow::class],
    version = 15
)

abstract class TremolaDatabase : RoomDatabase(){
    abstract fun contactDAO(): ContactDAO
    abstract fun logDAO() :    LogEntryDAO
    abstract fun pubDAO() :    PubDAO
    // not used for now:
    abstract fun blobDAO():    Notused_BlobDAO
    abstract fun followDAO() : Notused_FollowDAO

    companion object: SingletonHolder<TremolaDatabase, Context>({
        Room.databaseBuilder(it, TremolaDatabase::class.java, "surfcity_db")
            .addCallback(object: RoomDatabase.Callback(){})
            .fallbackToDestructiveMigration()
            .build()
    })
}