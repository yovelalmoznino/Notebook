package com.example.notebook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.notebook.data.db.Converters
import com.example.notebook.data.db.dao.FolderDao
import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.dao.PageDao
import com.example.notebook.data.db.entity.FolderEntity
import com.example.notebook.data.db.entity.NotebookEntity
import com.example.notebook.data.db.entity.PageEntity

@Database(
    entities = [FolderEntity::class, NotebookEntity::class, PageEntity::class],
    version = 3, // שינינו ל-3 בגלל שהוספנו את backgroundType ל-PageEntity
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao

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
                    .fallbackToDestructiveMigration() // ימחק את הנתונים הישנים וייצור את הטבלאות מחדש עם העמודה החדשה
                    .build()
                    .also { INSTANCE = it }
            }
    }
}