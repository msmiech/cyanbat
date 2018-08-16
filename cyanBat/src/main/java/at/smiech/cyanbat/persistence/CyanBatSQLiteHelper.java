package at.smiech.cyanbat.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


/**
 * Reference: Lecture
 *
 * @author kittysCode
 */
public class CyanBatSQLiteHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "ScoreGame.db";

    private final String SQL_CREATE_ENTRIES = "CREATE TABLE IF NOT EXISTS " + ScoreContract.ScoreEntry.TABLE_NAME +
            " (" + ScoreContract.ScoreEntry._ID + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_ID + " autoincrement," +
            ScoreContract.ScoreEntry.COLUMN_NAME_USERNAME + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_USERNAME + "," +
            ScoreContract.ScoreEntry.COLUMN_NAME_SCORE + " " + ScoreContract.ScoreEntry.COLUMN_TYPE_SCORE + ");";

    private final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + ScoreContract.ScoreEntry.TABLE_NAME;

    public CyanBatSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /* Called on first db access */
    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(CyanBatSQLiteHelper.class.getName(), "Creating tables in DB");
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    /**/
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(CyanBatSQLiteHelper.class.getName(), "Upgrading database from version " +
                oldVersion + " to " + newVersion);
        dropTables(db);
        onCreate(db);
    }

    private void dropTables(SQLiteDatabase db) {
        Log.d(CyanBatSQLiteHelper.class.getName(), "Dropping all tables");
        db.execSQL(SQL_DELETE_ENTRIES);
    }
}
