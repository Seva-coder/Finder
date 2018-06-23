package ru.seva.finder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class dBase extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "phones_db";
    public static final String PHONES_TABLE_OUT = "phones";
    public static final String PHONES_TABLE_IN = "phones_to_answer";
    public static final String PHONES_COL = "phone";  //одинаковая для обоих таблиц


    public dBase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + PHONES_TABLE_OUT +
                        " (_id integer primary key autoincrement, " +
                        PHONES_COL + " text);"
        );

        db.execSQL("CREATE TABLE " + PHONES_TABLE_IN + " (_id integer primary key autoincrement, " + PHONES_COL + " text);");


        db.execSQL(
                "CREATE TABLE history (_id integer primary key autoincrement, phone text, lat real, lon real, height real, speed real, direction real, acc integer, date text, bat text)"
        );

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
