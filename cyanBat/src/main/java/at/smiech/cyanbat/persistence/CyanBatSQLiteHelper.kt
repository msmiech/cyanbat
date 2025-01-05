package at.smiech.cyanbat.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log


/**
 * Reference: Lecture
 *
 * @author kittysCode
 */
class CyanBatSQLiteHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val SQL_CREATE_ENTRIES = "CREATE TABLE IF NOT EXISTS " + ScoreContract.ScoreEntry.TABLE_NAME +
            " (" + ScoreContract.ScoreEntry.COLUMN_NAME_ID + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_ID + " autoincrement," +
            ScoreContract.ScoreEntry.COLUMN_NAME_USERNAME + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_USERNAME + "," +
            ScoreContract.ScoreEntry.COLUMN_NAME_SCORE + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_SCORE + ");"

    private val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + ScoreContract.ScoreEntry.TABLE_NAME

    /* Called on first db access */
    override fun onCreate(db: SQLiteDatabase) {
        Log.d(CyanBatSQLiteHelper::class.java.name, "Creating tables in DB")
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    /**/
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(CyanBatSQLiteHelper::class.java.name, "Upgrading database from version " +
                oldVersion + " to " + newVersion)
        dropTables(db)
        onCreate(db)
    }

    private fun dropTables(db: SQLiteDatabase) {
        Log.d(CyanBatSQLiteHelper::class.java.name, "Dropping all tables")
        db.execSQL(SQL_DELETE_ENTRIES)
    }

    companion object {
        val DATABASE_VERSION = 1
        val DATABASE_NAME = "ScoreGame.db"
    }
}
