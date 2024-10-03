import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.slf4j.MDC.put

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "things.db"
        private const val DATABASE_VERSION = 2
        const val TABLE_NAME = "things"
        private const val COLUMN_ID = "id"
        const val COLUMN_THING_NAME = "thing_name"
        private const val COLUMN_THING_ID = "thing_id"
        private const val COLUMN_THING_KEY = "thing_key"
        const val COLUMN_IP_ADDRESS = "ip_address"  // New IP address column
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_THING_NAME + " TEXT, "
                + COLUMN_THING_ID + " TEXT, "
                + COLUMN_THING_KEY + " TEXT, "
                + COLUMN_IP_ADDRESS + " TEXT" + ")")  // Include the new IP address column
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // Insert a thing with IP address
    fun insertThing(
        thingName: String,
        thingId: String,
        thingKey: String,
        ipAddress: String
    ): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues().apply {
            put(COLUMN_THING_NAME, thingName)
            put(COLUMN_THING_ID, thingId)
            put(COLUMN_THING_KEY, thingKey)
            put(COLUMN_IP_ADDRESS, ipAddress)  // Save the IP address
        }
        return db.insert(TABLE_NAME, null, contentValues)
    }
}
