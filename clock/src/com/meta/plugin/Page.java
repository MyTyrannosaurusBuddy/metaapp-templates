package com.meta.plugin;

import android.view.View;

interface Page {
    View view();

    void onTick();

    void onShown();

    void onDestroy();
}
