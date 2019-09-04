package at.smiech.cyanbat.persistence

import android.provider.BaseColumns

/**
 * Reference: Lecture
 *
 * @author kittysCode
 */
class ScoreContract {

    /**
     * Provides constants for an entry in Score table. BaseColumns interface declares _ID = "id" constant.
     */
    abstract class ScoreEntry : BaseColumns {
        companion object {
            val TABLE_NAME = "highscore"

            val COLUMN_NAME_ID = "id"
            val COLUMN_TYPE_ID = "INTEGER PRIMARY KEY"

            val COLUMN_NAME_USERNAME = "username"
            val COLUMN_TYPE_USERNAME = "TEXT"

            val COLUMN_NAME_SCORE = "score"
            val COLUMN_TYPE_SCORE = "INTEGER"
        }
    }

}
