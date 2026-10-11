package app.camplanner.data.repo

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.settings.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Whole-app backup as one JSON file: every table except the alarm bookkeeping, plus settings.
 * Restoring replaces everything.
 */
class BackupService(
    private val db: CamPlannerDatabase,
    private val settings: SettingsStore,
    private val onRestored: () -> Unit,
) {
    private val tables = listOf(
        "subject", "subject_alias", "series", "event", "event_override", "location", "assignment",
        "reading_log", "exercise", "exercise_set", "run_log", "checkin",
    )

    suspend fun export(out: OutputStream) {
        val root = JSONObject()
            .put("app", "Cam Planner")
            .put("format", 1)
            .put("schema", db.openHelper.readableDatabase.version)
            .put("exportedAt", System.currentTimeMillis())
        val data = JSONObject()
        val sql = db.openHelper.readableDatabase
        tables.forEach { table ->
            val rows = JSONArray()
            sql.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(c.getColumnName(i), JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(c.getColumnName(i), c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(c.getColumnName(i), c.getDouble(i))
                            else -> row.put(c.getColumnName(i), c.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            data.put(table, rows)
        }
        root.put("tables", data).put("settings", settings.export())
        out.bufferedWriter().use { it.write(root.toString(1)) }
    }

    /** Throws IllegalArgumentException with a readable message if the file isn't a backup. */
    suspend fun restore(input: InputStream) {
        val root = try {
            JSONObject(input.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a Cam Planner backup.")
        }
        if (root.optString("app") != "Cam Planner") throw IllegalArgumentException("This file isn't a Cam Planner backup.")
        val data = root.getJSONObject("tables")
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            tables.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
            sql.execSQL("DELETE FROM scheduled_alarm")
            tables.forEach { table ->
                val rows = data.optJSONArray(table) ?: return@forEach
                val columns = sql.query("SELECT * FROM `$table` LIMIT 0").use { c -> c.columnNames.toSet() }
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val values = ContentValues()
                    row.keys().forEach { k ->
                        if (k !in columns) return@forEach // a column from a newer version
                        when (val v = row.get(k)) {
                            JSONObject.NULL -> values.putNull(k)
                            is Int -> values.put(k, v.toLong())
                            is Long -> values.put(k, v)
                            is Double -> values.put(k, v)
                            is Boolean -> values.put(k, if (v) 1L else 0L)
                            else -> values.put(k, v.toString())
                        }
                    }
                    sql.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
        }
        root.optJSONObject("settings")?.let { settings.import(it) }
        onRestored()
    }
}
