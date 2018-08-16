package at.smiech.cyanbat.persistence;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * References:
 * Multimedia UE lecture
 * http://www.vogella.com/tutorials/AndroidSQLite/article.html#tutorialusecp_overview
 *
 * @author kittysCode
 */
public class ScoreProvider extends ContentProvider {

    private CyanBatSQLiteHelper sqLiteHelper;

    // used for the UriMacher
    private static final int SCORE = 10;
    private static final int SCORE_ID = 20;

    private static final String AUTHORITY = "org.catworx.ducklingsflight.persistence";

    private static final String BASE_PATH = ScoreContract.ScoreEntry.TABLE_NAME;
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH);

    public static final String SELECTION_HIGHSCORE = "MAX(" + ScoreContract.ScoreEntry.COLUMN_NAME_SCORE + ") AS highscore";

    private static final UriMatcher URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URIMatcher.addURI(AUTHORITY, BASE_PATH, SCORE); //Matches a content URI for all rows in table
        URIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", SCORE_ID); //Matches a content URI for single rows in table
    }

    @Override
    public boolean onCreate() {
        this.sqLiteHelper = new CyanBatSQLiteHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        //check if the caller has requested a column which does not exists
        checkColumns(projection);

        queryBuilder.setTables(ScoreContract.ScoreEntry.TABLE_NAME);
        int uriType = URIMatcher.match(uri);

        switch (uriType) {
            // If the incoming URI was for all rows in score table
            case SCORE:
                break;
            // If the incoming URI was for a single row
            case SCORE_ID:
                // adding the ID to the original query
                queryBuilder.appendWhere(ScoreContract.ScoreEntry._ID + "="
                        + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        SQLiteDatabase db = sqLiteHelper.getWritableDatabase();
        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = URIMatcher.match(uri);
        SQLiteDatabase sqLiteDatabase = sqLiteHelper.getWritableDatabase();
        long id = 0;

        switch (uriType) {
            case SCORE:
                id = sqLiteDatabase.insert(ScoreContract.ScoreEntry.TABLE_NAME, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unkown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);

        return Uri.parse(BASE_PATH + "/" + id);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = URIMatcher.match(uri);
        SQLiteDatabase sqLiteDatabase = sqLiteHelper.getWritableDatabase();
        int rowsDeleted;
        switch (uriType) {
            case SCORE:
                rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case SCORE_ID:
                String id = uri.getLastPathSegment();
                if (TextUtils.isEmpty(selection)) {
                    rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, ScoreContract.ScoreEntry._ID + "=" + id, null);
                } else {
                    rowsDeleted = sqLiteDatabase.delete(ScoreContract.ScoreEntry.TABLE_NAME, ScoreContract.ScoreEntry._ID + "=" + id + " and " + selection, selectionArgs);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }


    private void checkColumns(String[] projection) {
        String[] available = {ScoreContract.ScoreEntry._ID,
                ScoreContract.ScoreEntry.COLUMN_NAME_USERNAME,
                ScoreContract.ScoreEntry.COLUMN_NAME_SCORE};
        if (projection != null) {
            Set<String> requestedColumns = new HashSet<>(Arrays.asList(projection));
            Set<String> availableColumns = new HashSet<>(Arrays.asList(available));
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }
}
