package at.smiech.cyanbat.persistence

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.text.TextUtils
import androidx.core.net.toUri

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
        this.sqLiteHelper = context?.let { CyanBatSQLiteHelper(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
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
                queryBuilder.appendWhere(
                    ScoreContract.ScoreEntry.COLUMN_NAME_ID + "="
                            + uri.lastPathSegment
                )

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        val db = sqLiteHelper!!.writableDatabase
        val cursor = queryBuilder.query(
            db, projection, selection,
            selectionArgs, null, null, sortOrder
        )
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
        val id: Long

        when (uriType) {
            SCORE -> id = sqLiteDatabase.insert(ScoreContract.ScoreEntry.TABLE_NAME, null, values)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        context!!.contentResolver.notifyChange(uri, null)

        return "$BASE_PATH/$id".toUri()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        sqLiteHelper?.writableDatabase?.let { sqLiteDatabase ->
            val uriType = URIMatcher.match(uri)
            val rowsDeleted: Int = when (uriType) {
                SCORE -> sqLiteDatabase.delete(
                    ScoreContract.ScoreEntry.TABLE_NAME,
                    selection,
                    selectionArgs
                )

                SCORE_ID -> {
                    val id = uri.lastPathSegment
                    if (TextUtils.isEmpty(selection)) {
                        sqLiteDatabase.delete(
                            ScoreContract.ScoreEntry.TABLE_NAME,
                            ScoreContract.ScoreEntry.COLUMN_NAME_ID + "=" + id,
                            null
                        )
                    } else {
                        sqLiteDatabase.delete(
                            ScoreContract.ScoreEntry.TABLE_NAME,
                            ScoreContract.ScoreEntry.COLUMN_NAME_ID + "=" + id + " and " + selection,
                            selectionArgs
                        )
                    }
                }

                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
            context?.contentResolver?.notifyChange(uri, null)
            return rowsDeleted
        } ?: throw IllegalStateException("Database not opened")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }


    private fun checkColumns(projection: Array<String>?) {
        val available = arrayOf(
            ScoreContract.ScoreEntry.COLUMN_NAME_ID,
            ScoreContract.ScoreEntry.COLUMN_NAME_USERNAME,
            ScoreContract.ScoreEntry.COLUMN_NAME_SCORE
        )
        if (projection != null) {
            val requestedColumns = HashSet(listOf(*projection))
            val availableColumns = HashSet(listOf(*available))
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw IllegalArgumentException("Unknown columns in projection")
            }
        }
    }

    companion object {
        // used for the UriMacher
        private const val SCORE = 10
        private const val SCORE_ID = 20

        private const val AUTHORITY = "at.msmiech.cyanbat.persistence"

        private val BASE_PATH = ScoreContract.ScoreEntry.TABLE_NAME

        private val URIMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            URIMatcher.addURI(
                AUTHORITY,
                BASE_PATH,
                SCORE
            ) //Matches a content URI for all rows in table
            URIMatcher.addURI(
                AUTHORITY,
                "$BASE_PATH/#",
                SCORE_ID
            ) //Matches a content URI for single rows in table
        }
    }
}
