package ru.seva.finder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class dBase extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 3;  //в весриях 1.0 и 1.1 была версия 1, c 1.5 - версия ДБ=3
    public static final String DATABASE_NAME = "phones_db";
    public static final String PHONES_TABLE_OUT = "phones";  //таблица номеров для запросов
    public static final String PHONES_TABLE_IN = "phones_to_answer";
    public static final String PHONES_COL = "phone";  //одинаковая для обоих таблиц
    public static final String NAME_COL = "name";  //одинаковая для обоих таблиц


    public dBase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + PHONES_TABLE_OUT + " (_id integer primary key autoincrement, " + PHONES_COL + " text, " + NAME_COL + " text DEFAULT \'unknown\');"
        );

        db.execSQL(
                "CREATE TABLE " + PHONES_TABLE_IN + " (_id integer primary key autoincrement, " + PHONES_COL + " text, " + NAME_COL + " text DEFAULT \'unknown\');"
        );


        db.execSQL(
                "CREATE TABLE history (_id integer primary key autoincrement, phone text, lat real, lon real, height real, speed real, direction real, acc integer, date text, bat text)"
        );

        db.execSQL("CREATE TABLE tracking_table (_id integer primary key autoincrement, phone text, track_id integer, lat real, lon real, speed real, date text)");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion >= 2) {
            // move from version 1 --> 2, adding NAME column
            db.execSQL("ALTER TABLE " + PHONES_TABLE_OUT + " ADD name text DEFAULT \'unknown\'");
            db.execSQL("ALTER TABLE " + PHONES_TABLE_IN + " ADD name text DEFAULT \'unknown\'");
        }
        if (newVersion == 3) {
            // creating tracking table (new feature of 1.5 version)
            db.execSQL("CREATE TABLE tracking_table (_id integer primary key autoincrement, phone text, track_id integer, lat real, lon real, speed real, date text)");
        }
    }
}