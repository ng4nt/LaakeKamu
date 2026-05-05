package fi.hh.laakekamu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import fi.hh.laakekamu.data.dao.LaakeDao
import fi.hh.laakekamu.data.dao.LaakkeenottoDao
import fi.hh.laakekamu.data.dao.MuistutusDao
import fi.hh.laakekamu.data.dao.OiremerkintaDao
import fi.hh.laakekamu.data.dao.ValintaDao
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Laakkeenotto
import fi.hh.laakekamu.data.entity.Muistutus
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.data.entity.Tyyppi
import fi.hh.laakekamu.data.entity.Yksikko

/**
 * The app's Room database. The schema is final from the start (validity columns included), so
 * it stays at version 1. A later schema change needs a version bump and a tested migration;
 * destructive migration is never allowed.
 */
@Database(
    entities = [
        Tyyppi::class,
        Yksikko::class,
        Laake::class,
        Muistutus::class,
        Laakkeenotto::class,
        Oiremerkinta::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun valintaDao(): ValintaDao

    abstract fun laakeDao(): LaakeDao

    abstract fun muistutusDao(): MuistutusDao

    abstract fun laakkeenottoDao(): LaakkeenottoDao

    abstract fun oiremerkintaDao(): OiremerkintaDao

    companion object {
        /** File name in the app's databases/ folder (used by /lk-adb dbcheck). */
        const val NAME = "laakekamu.db"

        /**
         * Builds the on-device database on [openHelperFactory] (SQLCipher's). Room opens it
         * lazily on the first query.
         */
        fun build(
            context: Context,
            openHelperFactory: SupportSQLiteOpenHelper.Factory
        ): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .openHelperFactory(openHelperFactory)
                .addCallback(SeedCallback)
                .build()
    }
}

/** Fills the wizard's choice lists when the database file is first created. */
internal object SeedCallback : RoomDatabase.Callback() {
    /** Medication types (Tyypit). */
    val TYYPIT = listOf("tabletti", "kapseli", "neste", "voide", "tipat", "inhalaatio", "jauhe")

    /** Dose units (Yksikot). */
    val YKSIKOT = listOf("mg", "g", "mcg", "ml", "%", "kpl", "tippaa")

    override fun onCreate(db: SupportSQLiteDatabase) {
        TYYPIT.forEach { db.execSQL("INSERT INTO Tyypit (tyyppi) VALUES (?)", arrayOf(it)) }
        YKSIKOT.forEach { db.execSQL("INSERT INTO Yksikot (yksikko) VALUES (?)", arrayOf(it)) }
    }
}
