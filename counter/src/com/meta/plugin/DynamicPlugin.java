package com.meta.plugin;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.meta.app.core.PluginContract;
import com.meta.app.bridge.ApiBridge;

public class DynamicPlugin implements PluginContract {
    private ViewGroup rootView;
    private TextView counterText;
    private TextView permissionText;
    private int counter = 0;
    private ApiBridge apiBridge;

    @Override
    public void onCreate(Context context, ViewGroup container, ApiBridge bridge) {
        this.apiBridge = bridge;
        
        // Create root layout
        rootView = new LinearLayout(context);
        rootView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        ((LinearLayout) rootView).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) rootView).setGravity(Gravity.CENTER);
        ((LinearLayout) rootView).setPadding(dp(24), dp(24), dp(24), dp(24));
        
        // Counter display
        counterText = new TextView(context);
        counterText.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        counterText.setTextSize(120);
        counterText.setTextColor(0xFFE0E0E0);
        counterText.setText("0");
        counterText.setGravity(Gravity.CENTER);
        
        // Button container
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(0, dp(32), 0, 0);
        
        // Minus button
        Button minusButton = new Button(context);
        minusButton.setLayoutParams(new ViewGroup.LayoutParams(dp(120), dp(120)));
        minusButton.setText("-");
        minusButton.setTextSize(48);
        minusButton.setTextColor(0xFFE0E0E0);
        minusButton.setBackgroundColor(0xFF6200EE);
        minusButton.setOnClickListener(v -> {
            counter--;
            updateCounter();
        });
        
        // Plus button
        Button plusButton = new Button(context);
        plusButton.setLayoutParams(new ViewGroup.LayoutParams(dp(120), dp(120)));
        plusButton.setText("+");
        plusButton.setTextSize(48);
        plusButton.setTextColor(0xFFE0E0E0);
        plusButton.setBackgroundColor(0xFF03DAC5);
        plusButton.setOnClickListener(v -> {
            counter++;
            updateCounter();
        });
        
        buttonContainer.addView(minusButton);
        buttonContainer.addView(plusButton);
        
        // Permission status text
        permissionText = new TextView(context);
        permissionText.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        permissionText.setTextSize(24);
        permissionText.setTextColor(0xFF03DAC5);
        permissionText.setText("Requesting camera permission...");
        permissionText.setGravity(Gravity.CENTER);
        permissionText.setPadding(0, dp(32), 0, 0);
        
        rootView.addView(counterText);
        rootView.addView(buttonContainer);
        rootView.addView(permissionText);
        
        container.addView(rootView);
        
        // Request camera permission immediately
        requestCameraPermission();
    }
    
    private void requestCameraPermission() {
        if (apiBridge != null) {
            apiBridge.requestPermissions(new String[]{"android.permission.CAMERA"}, 
                new ApiBridge.PermissionCallback() {
                    @Override
                    public void onResult(boolean[] granted) {
                        if (granted.length > 0 && granted[0]) {
                            permissionText.setText("Camera permission: GRANTED");
                            permissionText.setTextColor(0xFF03DAC5);
                        } else {
                            permissionText.setText("Camera permission: DENIED");
                            permissionText.setTextColor(0xFFFF5252);
                        }
                    }
                });
        }
    }
    
    private void updateCounter() {
        counterText.setText(String.valueOf(counter));
    }
    
    private int dp(int value) {
        float density = rootView.getContext().getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    @Override
    public void onDestroy() {
        // Cleanup if needed
    }

    @Override
    public String getTitle() {
        return "Counter App";
    }
}
