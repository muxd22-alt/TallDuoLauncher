package com.google.android.libraries.launcherclient;

interface ILauncherOverlayCallback {
    void overlayScrollChanged(float progress) = 0;
    void overlayStatusChanged(int status) = 1;
}
