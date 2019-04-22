package ru.seva.finder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


class dBase extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 3;  //in versions 1.0 and 1.1 was 1st db version, since 1.5 - version db 3
    private static final String DATABASE_NAME = "phones_db";
    static final String PHONES_TABLE_OUT = "phones";  //table of phone numbers to request
    static final String PHONES_TABLE_IN = "phones_to_answer";
    static final String PHONES_COL = "phone";  //same for both tables
    static final String NAME_COL = "name";  //same for both tables


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

    }
}