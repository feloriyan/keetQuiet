package io.github.feloriyan.keetquiet.di

import android.content.Context
import androidx.room.Room
import io.github.feloriyan.keetquiet.data.AppDatabase
import io.github.feloriyan.keetquiet.data.TranscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @ApplicationScope
    @Singleton
    @Provides
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "voice_transcriber_db"
        ).build()
    }

    @Provides
    fun provideTranscriptionDao(database: AppDatabase): TranscriptionDao {
        return database.transcriptionDao()
    }

    @Provides
    @Singleton
    fun provideFileLogger(@ApplicationContext context: Context): io.github.feloriyan.keetquiet.util.FileLogger {
        return io.github.feloriyan.keetquiet.util.FileLogger(context)
    }
}
