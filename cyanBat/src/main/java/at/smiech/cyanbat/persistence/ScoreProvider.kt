package at.smiech.cyanbat.persistence

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.text.TextUtils

import java.util.Arrays
import java.util.HashSet

/**
 * References:
 * Multimedia UE lecture
 * http://www.vogella.com/tutorials/AndroidSQLite/article.html#tutorialusecp_overview
 *
 * @author kittysCode
 */
class ScoreProvider : ContentProvider() {

    private var sqLiteHelper: CyanBatSQLiteHelper? = null

    override fun onCreate(): Boolean {
        this.sqLiteHelper = CyanBatSQLiteHelper(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {

        val queryBuilder = SQLiteQueryBuilder()
        //check if the caller has requested a column which does not exists
        checkColumns(projection)

        queryBuilder.tables = ScoreContract.ScoreEntry.TABLE_NAME
        val uriType = URIMatcher.match(uri)

        when (uriType) {
            // If the incoming URI was for all rows in score table
            SCORE -> {
            }
            // If the incoming URI was for a single row
            SCORE_ID ->
                // adding the ID to the original query
                queryBuilder.appendWhere(ScoreContract.ScoreEntry.COLUMN_NAME_ID + "="
                        + uri.lastPathSegment)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        val db = sqLiteHelper!!.writableDatabase
        val cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder)
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(context!!.contentResolver, uri)

        return cursor
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val uriType = URIMatcher.match(uri)
        val sqLiteDatabase = sqLiteHelper!!.writableDatabase
        var id: Long

        when (uriType) {
            SCORE -> id = sqLiteDatabase.insert(ScoreContract.ScoreEntry.TABLE_NAME, null, values)
            else -> throw IllegalArgumentException("Unkown URI: $uri")
        }
        context!!.contentResolver.notifyChange(uri, null)

        return Uri.parse("$BASE_PATH/$id")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val uriType = URIMatcher.match(uri)
        val sqLiteDatabase = sqLiteHelper!!.writableDatabase
        val rowsDeleted: Int
        when (uriType) {
            SCORE -> rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, selection, selectionArgs)
            SCORE_ID -> {
                val id = uri.lastPathSegment
                if (TextUtils.isEmpty(selection)) {
                    rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, ScoreContract.ScoreEntry.COLUMN_NAME_ID + "=" + id, null)
                } else {
                    rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, ScoreContract.ScoreEntry.COLUMN_NAME_ID + "=" + id + " and " + selection, selectionArgs)
                }
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        context!!.contentResolver.notifyChange(uri, null)
        return rowsDeleted
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }


    private fun checkColumns(projection: Array<String>?) {
        val available = arrayOf(ScoreContract.ScoreEntry.COLUMN_NAME_ID, ScoreContract.ScoreEntry.COLUMN_NAME_USERNAME, ScoreContract.ScoreEntry.COLUMN_NAME_SCORE)
        if (projection != null) {
            val requestedColumns = HashSet(Arrays.asList(*projection))
            val availableColumns = HashSet(Arrays.asList(*available))
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw IllegalArgumentException("Unknown columns in projection")
            }
        }
    }

    companion object {

        // used for the UriMacher
        private val SCORE = 10
        private val SCORE_ID = 20

        private val AUTHORITY = "org.catworx.ducklingsflight.persistence"

        private val BASE_PATH = ScoreContract.ScoreEntry.TABLE_NAME
        val CONTENT_URI = Uri.parse("content://" + AUTHORITY
                + "/" + BASE_PATH)

        val SELECTION_HIGHSCORE = "MAX(" + ScoreContract.ScoreEntry.COLUMN_NAME_SCORE + ") AS highscore"

        private val URIMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            URIMatcher.addURI(AUTHORITY, BASE_PATH, SCORE) //Matches a content URI for all rows in table
            URIMatcher.addURI(AUTHORITY, "$BASE_PATH/#", SCORE_ID) //Matches a content URI for single rows in table
        }
    }
}
