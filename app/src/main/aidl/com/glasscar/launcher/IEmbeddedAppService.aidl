package com.glasscar.launcher;
import android.view.MotionEvent;
import android.os.IBinder;

// Available only through the user's explicitly authorized Shizuku connection.
interface IEmbeddedAppService {
    String startApp(int displayId, String targetPackage, IBinder lifetime) = 0;
    oneway void touch(int displayId, in MotionEvent event) = 1;
    oneway void back(int displayId) = 2;
    String status(int displayId) = 3;
    void closeSession() = 4;
    void destroy() = 16777114;
}

