package com.google.android.libraries.launcherclient;

interface ILauncherOverlayCallback {
    void overlayScrollChanged(float progress);
    void overlayStatusChanged(int status);
}
