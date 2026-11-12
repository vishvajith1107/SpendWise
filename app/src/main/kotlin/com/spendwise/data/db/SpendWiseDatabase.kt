package com.spendwise.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        RecurringRuleEntity::class,
        CategoryEntity::class,
        MerchantCategoryRuleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SpendWiseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantCategoryRuleDao(): MerchantCategoryRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isCredit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN includeInBudget INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        category TEXT NOT NULL,
                        isCredit INTEGER NOT NULL,
                        includeInBudget INTEGER NOT NULL,
                        frequency TEXT NOT NULL,
                        nextDueEpochDay INTEGER NOT NULL,
                        autoLog INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        lastLoggedEpochDay INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromBudget INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        `key` TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        colorHex INTEGER NOT NULL,
                        iconKey TEXT NOT NULL,
                        isCreditCategory INTEGER NOT NULL,
                        isBuiltIn INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_category_rules (
                        normalizedMerchant TEXT NOT NULL,
                        categoryKey TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(normalizedMerchant)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
