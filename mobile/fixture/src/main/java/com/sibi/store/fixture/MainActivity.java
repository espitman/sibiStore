package com.sibi.store.fixture;
public class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("Sibi Store installation verified");
        text.setTextColor(0xffffc107); text.setTextSize(28); text.setGravity(android.view.Gravity.CENTER);
        text.setBackgroundColor(0xff050505); setContentView(text);
    }
}
