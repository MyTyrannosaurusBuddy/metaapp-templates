package com.meta.plugin;

import android.content.Context;
import android.view.ViewGroup;

import com.meta.app.core.PluginContract;
import com.meta.app.bridge.ApiBridge;

public class DynamicPlugin implements PluginContract {

    private CalculatorView rootView;

    @Override
    public void onCreate(Context context, ViewGroup container, ApiBridge bridge) {
        rootView = new CalculatorView(context);
        container.addView(rootView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onDestroy() {
        rootView = null;
    }

    @Override
    public String getTitle() {
        return "计算器";
    }
}
