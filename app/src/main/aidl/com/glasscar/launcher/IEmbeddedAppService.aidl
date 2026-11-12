package com.glasscar.launcher;
import android.view.MotionEvent;
import android.os.IBinder;

// Available only through the user's explicitly authorized Shizuku connection.
interface IEmbeddedAppService {
    String startApp(int displayId, String targetPackage, IBinder lifetime);
    oneway void touch(int displayId, in MotionEvent event);
    oneway void back(int displayId);
    String status(int displayId);
    void closeSession();
    void destroy() = 16777114;
}
