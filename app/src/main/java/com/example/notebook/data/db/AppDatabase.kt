package com.example.notebook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.notebook.data.db.dao.FolderDao
import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.entity.FolderEntity
import com.example.notebook.data.db.entity.NotebookEntity
import androidx.room.TypeConverters // חשוב!
import com.example.notebook.data.db.Converters
import com.example.notebook.data.db.entity.PageEntity
import com.example.notebook.data.db.dao.PageDao

@Database(
    entities = [FolderEntity::class, NotebookEntity::class, PageEntity::class], // הוספת PageEntity
    version = 2, // עדכון גרסה ל-2 בגלל שינוי המבנה
    exportSchema = true
)
@TypeConverters(Converters::class) //
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao // הוספת ה-DAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pastelnote.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}