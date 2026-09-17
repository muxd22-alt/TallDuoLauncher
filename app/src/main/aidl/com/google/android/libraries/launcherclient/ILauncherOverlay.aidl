package com.google.android.libraries.launcherclient;


import android.os.Bundle;

interface ILauncherOverlay {
    void startScroll();
    void overlayScroll(float progress);
    void endScroll();
    void windowAttached(in Bundle options, ILauncherOverlayCallback callback, int clientOptions);
    void windowDetached(boolean isChangingConfigurations);
    void closeOverlay(int options);
    void onPause();
    void onResume();
    void openOverlay(int options);
    void requestVoiceDetection(boolean start);
    String getVoiceSearchLanguage();
    boolean isVoiceDetectionRunning();
    boolean hasOverlayContent();
    void windowAttached2(in Bundle options, ILauncherOverlayCallback callback);
    void windowDetached2(boolean isChangingConfigurations);
    void unused_method();
}
