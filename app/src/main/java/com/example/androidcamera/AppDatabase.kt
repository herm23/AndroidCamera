package com.example.androidcamera

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "colors")
data class MyColor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdActivityInMillis: Long, //Tempo fisso in milli secondi, indica quando è stata creata l'attività
    val relativeToSpanInMillis: Long, //tempo relativo in milli secondi rispetto a quello precedente, questo è stato creato per mostrare i dati nel grafico
    val avgRed: Float,
    val avgGreen: Float,
    val avgBlue: Float
)

@Dao
interface UserDao {
    @Insert
    suspend fun insert(color: MyColor)

    @Insert
    suspend fun insertAll(colors: ArrayList<MyColor>)

    @Query("SELECT * FROM colors")
    suspend fun getAllColors(): List<MyColor>

    @Query("DELETE FROM colors")
    suspend fun deleteAll()
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        val tempInstance = INSTANCE
        if (tempInstance != null) {
            return tempInstance
        }
        synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).build()
            INSTANCE = instance
            return instance
        }
    }
}


@Database(entities = [MyColor::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}