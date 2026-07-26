package com.avapod.app.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.avapod.app.models.Category
import com.avapod.app.models.Podcast

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "AvapodDB"
        private const val DATABASE_VERSION = 5

        private const val TABLE_PODCASTS = "podcasts"
        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_AD_CONFIG = "ad_config"
        private const val TABLE_MESSAGES = "app_messages"

        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_DESC = "description"
        private const val KEY_CAT_ID = "category_id"
        private const val KEY_RSS = "rss_url"
        private const val KEY_THUMB = "thumbnail_url"
        private const val KEY_TRENDING = "is_trending"
        private const val KEY_FEATURED = "is_featured"
        private const val KEY_CAT_NAME = "name"
        private const val KEY_CAT_ICON = "icon"
        private const val KEY_MESSAGE_KEY = "message_key"
        private const val KEY_MESSAGE_TEXT = "text"
        private const val KEY_MESSAGE_DATE = "date"

        private const val KEY_BANNER_ENABLED = "ad_banner_enabled"
        private const val KEY_INTERSTITIAL_ENABLED = "ad_interstitial_enabled"
        private const val KEY_REWARDED_ENABLED = "ad_rewarded_enabled"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createPodcastTable = ("CREATE TABLE $TABLE_PODCASTS ("
                + "$KEY_ID TEXT PRIMARY KEY,"
                + "$KEY_TITLE TEXT,"
                + "$KEY_ARTIST TEXT,"
                + "$KEY_DESC TEXT,"
                + "$KEY_CAT_ID TEXT,"
                + "$KEY_RSS TEXT,"
                + "$KEY_THUMB TEXT,"
                + "$KEY_TRENDING INTEGER,"
                + "$KEY_FEATURED INTEGER)")

        val createCategoryTable = ("CREATE TABLE $TABLE_CATEGORIES ("
                + "$KEY_ID TEXT PRIMARY KEY,"
                + "$KEY_CAT_NAME TEXT,"
                + "$KEY_CAT_ICON TEXT)")

        val createAdConfigTable = ("CREATE TABLE $TABLE_AD_CONFIG ("
                + "$KEY_ID INTEGER PRIMARY KEY,"
                + "$KEY_BANNER_ENABLED INTEGER,"
                + "$KEY_INTERSTITIAL_ENABLED INTEGER,"
                + "$KEY_REWARDED_ENABLED INTEGER)")

        val createMessagesTable = ("CREATE TABLE $TABLE_MESSAGES ("
                + "$KEY_MESSAGE_KEY TEXT PRIMARY KEY,"
                + "$KEY_MESSAGE_TEXT TEXT,"
                + "$KEY_MESSAGE_DATE TEXT)")

        db?.execSQL(createPodcastTable)
        db?.execSQL(createCategoryTable)
        db?.execSQL(createAdConfigTable)
        db?.execSQL(createMessagesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // اضافه کردن ستون is_featured برای ورژن ۴
        if (oldVersion < 4) {
            try {
                db?.execSQL("ALTER TABLE $TABLE_PODCASTS ADD COLUMN $KEY_FEATURED INTEGER DEFAULT 0")
            } catch (e: Exception) {
                // ستون ممکنه قبلاً وجود داشته باشه
            }
        }

        // اضافه کردن جدول پیام‌ها برای ورژن ۵
        if (oldVersion < 5) {
            try {
                val createMessagesTable = ("CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES ("
                        + "$KEY_MESSAGE_KEY TEXT PRIMARY KEY,"
                        + "$KEY_MESSAGE_TEXT TEXT,"
                        + "$KEY_MESSAGE_DATE TEXT)")
                db?.execSQL(createMessagesTable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun insertPodcasts(podcasts: List<Podcast>) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_PODCASTS, null, null)
            for (pod in podcasts) {
                val values = ContentValues().apply {
                    put(KEY_ID, pod.id)
                    put(KEY_TITLE, pod.title)
                    put(KEY_ARTIST, pod.artist)
                    put(KEY_DESC, pod.description)
                    put(KEY_CAT_ID, pod.category_id)
                    put(KEY_RSS, pod.rss_url)
                    put(KEY_THUMB, pod.thumbnail_url)
                    put(KEY_TRENDING, if (pod.is_trending) 1 else 0)
                    put(KEY_FEATURED, if (pod.is_featured) 1 else 0)
                }
                db.insert(TABLE_PODCASTS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllPodcasts(): List<Podcast> {
        val list = mutableListOf<Podcast>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_PODCASTS", null)

        if (cursor.moveToFirst()) {
            do {
                list.add(Podcast(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    artist = cursor.getString(2),
                    description = cursor.getString(3),
                    category_id = cursor.getString(4),
                    rss_url = cursor.getString(5),
                    thumbnail_url = cursor.getString(6),
                    is_trending = cursor.getInt(7) == 1,
                    is_featured = if (cursor.columnCount > 8) cursor.getInt(8) == 1 else false
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun insertCategories(categories: List<Category>) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CATEGORIES, null, null)
            for (cat in categories) {
                val values = ContentValues().apply {
                    put(KEY_ID, cat.id)
                    put(KEY_CAT_NAME, cat.name)
                    put(KEY_CAT_ICON, cat.icon)
                }
                db.insert(TABLE_CATEGORIES, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CATEGORIES", null)

        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(KEY_ID)
            val nameIndex = cursor.getColumnIndex(KEY_CAT_NAME)
            val iconIndex = cursor.getColumnIndex(KEY_CAT_ICON)

            do {
                if (idIndex != -1 && nameIndex != -1 && iconIndex != -1) {
                    list.add(Category(
                        id = cursor.getString(idIndex),
                        name = cursor.getString(nameIndex),
                        icon = cursor.getString(iconIndex)
                    ))
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun insertOrUpdateAdConfig(banner: Boolean, interstitial: Boolean, rewarded: Boolean) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, 1)
            put(KEY_BANNER_ENABLED, if (banner) 1 else 0)
            put(KEY_INTERSTITIAL_ENABLED, if (interstitial) 1 else 0)
            put(KEY_REWARDED_ENABLED, if (rewarded) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_AD_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isAdEnabled(adType: String): Boolean {
        var isEnabled = when (adType) {
            "banner" -> true
            else -> false
        }

        val db = this.readableDatabase

        try {
            val cursor = db.rawQuery("SELECT * FROM $TABLE_AD_CONFIG WHERE $KEY_ID = 1", null)

            if (cursor.moveToFirst()) {
                val columnName = when (adType) {
                    "banner" -> KEY_BANNER_ENABLED
                    "interstitial" -> KEY_INTERSTITIAL_ENABLED
                    "rewarded" -> KEY_REWARDED_ENABLED
                    else -> ""
                }

                val index = cursor.getColumnIndex(columnName)
                if (index != -1) {
                    isEnabled = cursor.getInt(index) == 1
                }
            }
            cursor.close()
        } catch (e: android.database.sqlite.SQLiteException) {
            if (e.message?.contains("no such table") == true) {
                try {
                    val writableDb = this.writableDatabase
                    val createAdConfigTable = ("CREATE TABLE IF NOT EXISTS $TABLE_AD_CONFIG ("
                            + "$KEY_ID INTEGER PRIMARY KEY,"
                            + "$KEY_BANNER_ENABLED INTEGER,"
                            + "$KEY_INTERSTITIAL_ENABLED INTEGER,"
                            + "$KEY_REWARDED_ENABLED INTEGER)")
                    writableDb.execSQL(createAdConfigTable)
                } catch (ignored: Exception) {}
            }
        }

        return isEnabled
    }

    fun saveMessage(key: String, text: String, date: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_MESSAGE_KEY, key)
            put(KEY_MESSAGE_TEXT, text)
            put(KEY_MESSAGE_DATE, date)
        }
        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMessage(key: String): com.avapod.app.models.AppMessage? {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MESSAGES WHERE $KEY_MESSAGE_KEY = ?", arrayOf(key))

        return try {
            if (cursor.moveToFirst()) {
                val textIndex = cursor.getColumnIndex(KEY_MESSAGE_TEXT)
                val dateIndex = cursor.getColumnIndex(KEY_MESSAGE_DATE)

                if (textIndex != -1 && dateIndex != -1) {
                    com.avapod.app.models.AppMessage(
                        text = cursor.getString(textIndex) ?: "",
                        date = cursor.getString(dateIndex) ?: ""
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    fun getAllMessages(): Map<String, com.avapod.app.models.AppMessage> {
        val map = mutableMapOf<String, com.avapod.app.models.AppMessage>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MESSAGES", null)

        try {
            if (cursor.moveToFirst()) {
                val keyIndex = cursor.getColumnIndex(KEY_MESSAGE_KEY)
                val textIndex = cursor.getColumnIndex(KEY_MESSAGE_TEXT)
                val dateIndex = cursor.getColumnIndex(KEY_MESSAGE_DATE)

                do {
                    if (keyIndex != -1 && textIndex != -1 && dateIndex != -1) {
                        val key = cursor.getString(keyIndex) ?: continue
                        map[key] = com.avapod.app.models.AppMessage(
                            text = cursor.getString(textIndex) ?: "",
                            date = cursor.getString(dateIndex) ?: ""
                        )
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            cursor.close()
        }
        return map
    }

    fun deletePodcast(podcastId: String) {
        val db = this.writableDatabase
        db.delete(TABLE_PODCASTS, "$KEY_ID = ?", arrayOf(podcastId))
    }
}