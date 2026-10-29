package com.syswonder.roboguide.hub;

import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Prevent phone screen from sleeping during robotics field experiment
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 2. Hardware safety: intercept volume down key as Emergency Stop
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(500);
            }
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().post(() -> {
                    getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('hardware-estop'));", null
                    );
                });
            }
            return true; // consumed
        }
        return super.onKeyDown(keyCode, event);
    }
}
