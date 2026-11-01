package com.smartvoice.input;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FloatingOverlayPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @CapacitorPlugin(name = "FloatingOverlay")
    public static class FloatingOverlayPlugin extends Plugin {
        @PluginMethod
        public void checkPermission(PluginCall call) {
            boolean canDraw = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                canDraw = Settings.canDrawOverlays(getContext());
            }
            JSObject ret = new JSObject();
            ret.put("granted", canDraw);
            call.resolve(ret);
        }

        @PluginMethod
        public void requestPermission(PluginCall call) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getContext())) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            call.resolve();
        }

        @PluginMethod
        public void startService(PluginCall call) {
            Intent serviceIntent = new Intent(getContext(), FloatingMicService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
            } else {
                getContext().startService(serviceIntent);
            }
            call.resolve();
        }

        @PluginMethod
        public void stopService(PluginCall call) {
            Intent serviceIntent = new Intent(getContext(), FloatingMicService.class);
            getContext().stopService(serviceIntent);
            call.resolve();
        }
    }
}
