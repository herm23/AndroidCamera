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

//AppDatabase si occupa di:
//1) Implementare un semplice database locale che gestisca i colori
//2) Rende disponibile il db a tuttal'applicazione

@Entity(tableName = "colors")
data class MyColor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdColorInMillis : Long,
    val avgRed: Int,
    val avgGreen: Int,
    val avgBlue: Int
)

@Dao
interface MyColorDao {
    @Insert
    suspend fun insert(color: MyColor)

    @Insert
    suspend fun insertAll(colors: ArrayList<MyColor>)

    @Query("SELECT * FROM colors")
    suspend fun getAllColors(): List<MyColor>

    @Query("DELETE FROM colors")
    suspend fun deleteAll()
}

@Database(entities = [MyColor::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun myColorDao(): MyColorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}