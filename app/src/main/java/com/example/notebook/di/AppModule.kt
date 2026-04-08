package com.example.notebook.di

import android.content.Context
import androidx.room.Room
import com.example.notebook.data.db.AppDatabase
import com.example.notebook.data.db.dao.FolderDao
import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.dao.PageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pastel_notebook_db"
        )
            .fallbackToDestructiveMigration() // מאפשר לאפליקציה לעלות גם אם הסכימה השתנתה
            .build()
    }

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideNotebookDao(db: AppDatabase): NotebookDao = db.notebookDao()

    @Provides
    fun providePageDao(db: AppDatabase): PageDao = db.pageDao()
}