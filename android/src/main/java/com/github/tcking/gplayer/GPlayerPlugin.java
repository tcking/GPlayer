package com.github.tcking.gplayer;

import android.content.Context;
import android.media.AudioManager;
import android.view.Window;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterNativeView;

/**
 * GPlayerPlugin
 */
public class GPlayerPlugin implements MethodCallHandler {
    private final Registrar registrar;

    private GPlayerPlugin(Registrar registrar) {
        this.registrar = registrar;
        PlayerManager.getInstance().onPluginInit(registrar);
    }

    // -----------------


    // -----------------

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "com.github.tcking/gplayer");
        final GPlayerPlugin plugin = new GPlayerPlugin(registrar);
        channel.setMethodCallHandler(plugin);

        registrar.addViewDestroyListener(new PluginRegistry.ViewDestroyListener() {
            @Override
            public boolean onViewDestroy(FlutterNativeView view) {
                plugin.onDestroy();
                return false; // We are not interested in assuming ownership of the NativeView.
            }
        });
    }

    private void onDestroy() {
        PlayerManager.getInstance().onPluginDestroy();
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        String fingerprint = call.argument("fingerprint");
        if (fingerprint == null) {
            result.error("fingerprint is null", null, null);
            return;
        }

        if (call.method.equals("init")) {
            PlayerManager.getInstance().createPlayer(VideoInfo.from((Map) call.arguments));
            result.success(null);
            return;
        }

        GiraffePlayer player = PlayerManager.getInstance().getPlayerByFingerprint(fingerprint);
        if (player == null) {
            result.error("can't find player for fingerprint:" + fingerprint, null, null);
            return;
        }

        if (call.method.equals("start")) {
            player.start();
            result.success(null);
        } else if (call.method.equals("pause")) {
            player.pause();
            result.success(null);
        }else if (call.method.equals("release")) {
            player.release();
            result.success(null);
        } else if (call.method.equals("getCurrentPosition")) {
            result.success(player.getCurrentPosition());
        } else if (call.method.equals("seekTo")) {
            System.out.println("seekTo:" + ((Map) call.arguments).get("position"));
            player.seekTo((Integer) ((Map) call.arguments).get("position"));
            result.success(null);
        } else if (call.method.equals("getAllInfo")) {
            AudioManager am = (AudioManager) registrar.context().getSystemService(Context.AUDIO_SERVICE);
            Map rsp = new HashMap<>();
            rsp.put("maxVolume", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            rsp.put("volume", am.getStreamVolume(AudioManager.STREAM_MUSIC));
            rsp.put("currentPosition", player.getCurrentPosition());


            Window window = registrar.activity().getWindow();
            rsp.put("screenBrightness", window.getAttributes().screenBrightness);

            result.success(rsp);
        } else if (call.method.equals("setStreamVolume")) {
            AudioManager am = (AudioManager) registrar.context().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (Integer) ((Map) call.arguments).get("volume"), 0);
            result.success(null);
        } else if (call.method.equals("setScreenBrightness")) {
            Window window = registrar.activity().getWindow();
            WindowManager.LayoutParams lpa = window.getAttributes();
            double brightness = (Double) ((Map) call.arguments).get("brightness");
            lpa.screenBrightness = (float) brightness;
            if (lpa.screenBrightness > 1.0f) {
                lpa.screenBrightness = 1.0f;
            } else if (lpa.screenBrightness < 0.01f) {
                lpa.screenBrightness = 0.01f;
            }
            window.setAttributes(lpa);
            result.success(null);
        } else {
            result.notImplemented();
        }
    }

}
