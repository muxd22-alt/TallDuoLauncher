package com.google.android.libraries.launcherclient;

import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;
import android.os.Bundle;

interface ILauncherOverlay {
    void startScroll() = 0;
    void endScroll() = 3;
    void windowAttached(in Bundle options, ILauncherOverlayCallback callback, int clientOptions) = 13;
    void windowAttached2(in Bundle options, ILauncherOverlayCallback callback) = 1;
    void windowDetached(boolean isChangingConfigurations) = 2;
    void closeOverlay(int options) = 5;
    void onPause() = 6;
    void onResume() = 7;
    void openOverlay(int options) = 8;
    void requestVoiceDetection(boolean start) = 9;
    String getVoiceSearchLanguage() = 10;
    boolean isVoiceDetectionRunning() = 11;
    boolean hasOverlayContent() = 12;
    void windowDetached2(boolean isChangingConfigurations) = 14;
}
