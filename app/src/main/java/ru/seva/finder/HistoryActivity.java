package ru.seva.finder;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class HistoryActivity extends AppCompatActivity {

    ListView list;
    Cursor cursor;
    dBase baseConnect;
    SQLiteDatabase db;
    SimpleCursorAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        list = (ListView) findViewById(R.id.lvHistory);

        //подрубаемся к базе
        baseConnect = new dBase(this);
        db = baseConnect.getWritableDatabase();

        String[] fromColons = {"name", "date"};
        int[] toViews = {android.R.id.text1, android.R.id.text2};
        cursor = db.rawQuery("SELECT history._id, history.phone, phones.name, history.date FROM history LEFT JOIN phones ON history.phone = phones.phone", null);

        //создаём адаптер
        adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2,
                cursor,
                fromColons,
                toViews,
                0);

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {  //замена налету пустого поля неизвестного номера на текст об этом
            public boolean setViewValue(View view, Cursor cursor, int column) {
                if (column == cursor.getColumnIndex("name") && cursor.getString(column) == null) {
                    TextView text = (TextView) view;
                    text.setText(getString(R.string.unknown_number, cursor.getString(1)));  //col index=1 - history.phone
                    return true;
                }
                return false;
            }
        });

        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showMenu(view, id);
            }
        });
    }


    protected void onResume() {
        super.onResume();
        updateList();
    }

    protected void onDestroy() {
        super.onDestroy();
        cursor.close();
        db.close();
    }


    public void showMenu(final View v, final long num_id) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.history_menu);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Cursor query = db.query("history", null,
                        "_id = ?", new String[] {Long.toString(num_id)},
                        null, null, null);  //получаем данные из всех колонок (возможно стоит перенести отсюда?)
                query.moveToFirst();
                Double lat = query.getDouble(query.getColumnIndex("lat"));
                Double lon = query.getDouble(query.getColumnIndex("lon"));

                switch (item.getItemId()) {
                    case R.id.internal_map__id:
                        //открыть свою встроенную карту
                        Intent start_map = new Intent(v.getContext(), MapsActivity.class);
                        start_map.putExtra("lat", lat);
                        start_map.putExtra("lon", lon);
                        start_map.putExtra("zoom", 15d);
                        Integer acc = query.getInt(query.getColumnIndex("acc"));
                        if (!query.isNull(query.getColumnIndex("acc"))) {
                            start_map.putExtra("accuracy", String.valueOf(acc) + getString(R.string.meters));
                        }
                        startActivity(start_map);
                        query.close();
                        return true;

                    case R.id.external_map_id:
                        //открыть внешней прогой
                        Uri intentUri = Uri.parse("geo:" + lat + "," + lon);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, intentUri);
                        if (mapIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(mapIntent);
                        } else {
                            Toast.makeText(HistoryActivity.this, R.string.no_map_app, Toast.LENGTH_LONG).show();
                        }
                        query.close();
                        return true;

                    case R.id.info_id:
                        TextView text = new TextView(v.getContext());
                        text.setTextIsSelectable(true);
                        text.setTextSize(24);
                        text.setPadding(20,5,5,5);

                        String phone = query.getString(query.getColumnIndex("phone"));
                        Cursor query2 = db.query(dBase.PHONES_TABLE_OUT, new String[] {dBase.NAME_COL},
                                "phone = ?", new String[] {phone},
                                null, null, null);  //второй запрос, тк имя в другой таблице
                        String name;
                        if (query2.moveToFirst()) {
                            name = query2.getString(query2.getColumnIndex("name"));
                        } else {
                            name = getString(R.string.unknown_number, phone);
                        }
                        text.append(name);
                        text.append("\n");
                        query2.close();

                        String date = query.getString(query.getColumnIndex("date"));
                        text.append(date);
                        text.append("\n");

                        String bat = query.getString(query.getColumnIndex("bat"));
                        if (!query.isNull(query.getColumnIndex("bat"))) {
                            text.append(getString(R.string.battery, bat));
                        }

                        text.append(getString(R.string.latitude, lat));
                        text.append(getString(R.string.longitude, lon));

                        Integer accuracy = query.getInt(query.getColumnIndex("acc"));
                        if (!query.isNull(query.getColumnIndex("acc"))) {
                            text.append(getString(R.string.accuracy, accuracy));
                        }

                        Double altitude = query.getDouble(query.getColumnIndex("height"));
                        if (!query.isNull(query.getColumnIndex("height"))) {
                            text.append(getString(R.string.altitude, altitude));
                        }

                        Float speed = query.getFloat(query.getColumnIndex("speed"));
                        if (!query.isNull(query.getColumnIndex("speed"))) {
                            text.append(getString(R.string.speed, speed));
                        }

                        Float direction = query.getFloat(query.getColumnIndex("direction"));
                        if (!query.isNull(query.getColumnIndex("direction"))) {
                            text.append(getString(R.string.course, direction));
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setView(text).setTitle(R.string.point_data).setPositiveButton("OK", null);

                        AlertDialog dialog = builder.create();
                        dialog.show();
                        query.close();
                        return true;

                    case R.id.del_marker_id:  //удаление тоски из истории, с предупреждением
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int witch) {
                                switch(witch) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        db.delete("history", "_id = ?", new String[] {Long.toString(num_id)});
                                        //обновление списка
                                        updateList();
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //отмена удаления, ничего не делаем
                                        break;
                                }
                            }
                        };
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(v.getContext());
                        builder2.setMessage(R.string.delete_warning).setPositiveButton(R.string.yes, dialogClickListener)
                                .setNegativeButton(R.string.no, dialogClickListener).show();
                        query.close();
                        return true;
                    default:
                        query.close();
                        return false;
                }
            }
        });
        menu.show();
    }

    public void updateList() {
        cursor = db.rawQuery("SELECT history._id, history.phone, phones.name, history.date FROM history LEFT JOIN phones ON history.phone = phones.phone", null);  //можно ли без копирования?
        adapter.swapCursor(cursor);
        adapter.notifyDataSetChanged();
    }
}
