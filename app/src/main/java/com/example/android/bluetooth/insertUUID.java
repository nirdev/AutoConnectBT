package com.example.android.bluetooth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class insertUUID extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insert_uuid);
    }

    //Onclick Insert
    public void insertUUID(View view) {

        EditText editText = (EditText) findViewById(R.id.edit_text);

        SharedPreferences.Editor editor = getSharedPreferences("MY_PREFS_NAME", MODE_PRIVATE).edit();
        editor.putBoolean("isFirst", false);
        editor.putString("UUIDString", editText.getText().toString());
        editor.apply();

        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
    }
}
