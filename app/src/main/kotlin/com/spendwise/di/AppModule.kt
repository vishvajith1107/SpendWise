package com.spendwise.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.spendwise.data.db.CategoryDao
import com.spendwise.data.db.RecurringRuleDao
import com.spendwise.data.db.SpendWiseDatabase
import com.spendwise.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spendwise_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpendWiseDatabase =
        Room.databaseBuilder(
            context,
            SpendWiseDatabase::class.java,
            "spendwise.db"
        )
            .addMigrations(
                SpendWiseDatabase.MIGRATION_1_2,
                SpendWiseDatabase.MIGRATION_2_3,
                SpendWiseDatabase.MIGRATION_3_4,
                SpendWiseDatabase.MIGRATION_4_5
            )
            .build()

    @Provides
    @Singleton
    fun provideTransactionDao(db: SpendWiseDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideRecurringRuleDao(db: SpendWiseDatabase): RecurringRuleDao = db.recurringRuleDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: SpendWiseDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
