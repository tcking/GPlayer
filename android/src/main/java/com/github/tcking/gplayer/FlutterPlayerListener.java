package com.github.tcking.gplayer;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import tv.danmaku.ijk.media.player.IjkTimedText;

/**
 * Created by TangChao on 2019/2/18.
 */
public class FlutterPlayerListener implements PlayerListener {
    private QueuingEventSink eventSink=new QueuingEventSink();
    private EventChannel eventChannel;

    public FlutterPlayerListener(String fingerprint) {

        eventChannel = new EventChannel(PlayerManager.getInstance().getRegistrar().messenger(),"com.github.tcking/gplayer/"+fingerprint);
        eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, EventChannel.EventSink eventSink) {
                FlutterPlayerListener.this.eventSink.setDelegate(eventSink);
            }

            @Override
            public void onCancel(Object o) {
                FlutterPlayerListener.this.eventSink.setDelegate(null);
            }
        });
    }

    @Override
    public void onPrepared(GiraffePlayer giraffePlayer) {
//        eventSink.success();
        HashMap p=new HashMap();
        p.put("event","onPrepared");
        p.put("duration",giraffePlayer.getDuration());
        eventSink.success(p);
    }

    @Override
    public void onBufferingUpdate(GiraffePlayer giraffePlayer, int percent) {

    }

    @Override
    public boolean onInfo(GiraffePlayer giraffePlayer, int what, int extra) {
        return false;
    }

    @Override
    public void onCompletion(GiraffePlayer giraffePlayer) {

    }

    @Override
    public void onSeekComplete(GiraffePlayer giraffePlayer) {
        HashMap p=new HashMap();
        p.put("event","onSeekComplete");
        p.put("position",giraffePlayer.getCurrentPosition());
        eventSink.success(p);
    }

    @Override
    public boolean onError(GiraffePlayer giraffePlayer, int what, int extra,String msg) {
        HashMap p=new HashMap();
        p.put("event","onError");
        p.put("what",what);
        p.put("extra",extra);
        p.put("msg",msg);
        eventSink.success(p);
        return true;
    }

    @Override
    public void onPause(GiraffePlayer giraffePlayer) {

    }

    @Override
    public void onRelease(GiraffePlayer giraffePlayer) {
        eventSink.endOfStream();
        eventChannel.setStreamHandler(null);
    }

    @Override
    public void onStart(GiraffePlayer giraffePlayer) {

    }

    @Override
    public void onTargetStateChange(int oldState, int newState) {
        HashMap p=new HashMap();
        p.put("event","onTargetStateChange");
        p.put("oldState",oldState);
        p.put("newState",newState);
        eventSink.success(p);
    }

    @Override
    public void onCurrentStateChange(int oldState, int newState) {
        HashMap p=new HashMap();
        p.put("event","onCurrentStateChange");
        p.put("oldState",oldState);
        p.put("newState",newState);
        eventSink.success(p);
    }

    @Override
    public void onDisplayModelChange(int oldModel, int newModel) {

    }

    @Override
    public void onPreparing(GiraffePlayer giraffePlayer) {

    }

    @Override
    public void onTimedText(GiraffePlayer giraffePlayer, IjkTimedText text) {

    }

    @Override
    public void onLazyLoadProgress(GiraffePlayer giraffePlayer, int progress) {
        Map<String,Object> p=new HashMap();
        p.put("event","onLazyLoadProgress");
        p.put("progress",progress);
        eventSink.success(p);
    }

    @Override
    public void onLazyLoadError(GiraffePlayer giraffePlayer, String message) {
        Map<String,Object> p=new HashMap();
        p.put("event","onLazyLoadError");
        p.put("message",message);
        eventSink.success(p);
    }

    @Override
    public void onSetDisplay(GiraffePlayer giraffePlayer, long id) {
        Map<String,Object> p=new HashMap();
        p.put("event","onSetDisplay");
        p.put("textureId",id);
        eventSink.success(p);
    }

    @Override
    public void onVideoSizeChanged(GiraffePlayer giraffePlayer, int videoWidth, int videoHeight) {
        Map<String,Object> p=new HashMap();
        p.put("event","onVideoSizeChanged");
        p.put("videoWidth",videoWidth);
        p.put("videoHeight",videoHeight);
        eventSink.success(p);
    }
}
