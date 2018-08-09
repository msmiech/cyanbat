package at.msmiech.cyanbat.persistence;

import android.provider.BaseColumns;

/**
 * Reference: Lecture
 *
 * @author kittysCode
 */
public class ScoreContract {

    /**
     * Provides constants for an entry in Score table. BaseColumns interface declares _ID = "id" constant.
     */
    public static abstract class ScoreEntry implements BaseColumns {
        public static final String TABLE_NAME = "highscore";

        public static final String COLUMN_TYPE_ID = "INTEGER PRIMARY KEY";

        public static final String COLUMN_NAME_USERNAME = "username";
        public static final String COLUMN_TYPE_USERNAME = "TEXT";

        public static final String COLUMN_NAME_SCORE = "score";
        public static final String COLUMN_TYPE_SCORE = "INTEGER";
    }

}
