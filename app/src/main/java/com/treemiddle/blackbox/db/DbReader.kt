package com.treemiddle.blackbox.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

object DbReader {

    private const val ROW_LIMIT = 200

    fun listJson(context: Context): String {
        val array = JSONArray()
        databases(context).forEach { name ->
            val obj = JSONObject()
            obj.put("db", name)
            obj.put("tables", JSONArray(tables(context, name)))
            array.put(obj)
        }
        return array.toString()
    }

    fun tableJson(context: Context, dbName: String, table: String): String {
        openReadOnly(context, dbName).use { db ->
            if (table !in tableNames(db)) {
                return JSONObject().put("error", "unknown table: $table").toString()
            }
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            db.rawQuery("SELECT * FROM $quoted LIMIT $ROW_LIMIT", null).use { cursor ->
                val columns = JSONArray()
                cursor.columnNames.forEach { columns.put(it) }
                val rows = JSONArray()
                while (cursor.moveToNext()) {
                    val row = JSONArray()
                    for (i in 0 until cursor.columnCount) {
                        row.put(cellValue(cursor, i))
                    }
                    rows.put(row)
                }
                return JSONObject()
                    .put("columns", columns)
                    .put("rows", rows)
                    .put("count", rows.length())
                    .toString()
            }
        }
    }

    private fun databases(context: Context): List<String> =
        context.databaseList()
            .filter { !it.endsWith("-wal") && !it.endsWith("-shm") && !it.endsWith("-journal") }
            .sorted()

    private fun tables(context: Context, dbName: String): List<String> =
        openReadOnly(context, dbName).use { tableNames(it) }

    private fun tableNames(db: SQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0))
            }
        }
        return names
    }

    private fun openReadOnly(context: Context, dbName: String): SQLiteDatabase {
        val path = context.getDatabasePath(dbName).absolutePath
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun cellValue(cursor: Cursor, index: Int): String = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
        Cursor.FIELD_TYPE_BLOB -> "<blob " + cursor.getBlob(index).size + " bytes>"
        else -> cursor.getString(index) ?: "null"
    }
}
