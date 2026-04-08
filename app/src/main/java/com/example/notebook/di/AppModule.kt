package com.example.notebook.di

import android.content.Context
import com.example.notebook.data.db.AppDatabase
import com.example.notebook.data.db.dao.FolderDao
import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.repository.FolderRepository
import com.example.notebook.data.repository.NotebookRepository
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    @Singleton
    fun provideNotebookDao(db: AppDatabase): NotebookDao = db.notebookDao()
}