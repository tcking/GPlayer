package com.github.tcking.gplayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by TangChao on 2019/2/16.
 */
public class PlayerManager {
    private final static PlayerManager instance=new PlayerManager();
    private Map<String,GiraffePlayer> players=new ConcurrentHashMap<>();

    public PluginRegistry.Registrar getRegistrar() {
        return registrar;
    }

    private PluginRegistry.Registrar registrar;

    public static PlayerManager getInstance() {
        return instance;
    }

    private PlayerManager(){

    }

    public GiraffePlayer getPlayerByFingerprint(String fingerprint) {
        return players.get(fingerprint);
    }

    public void createPlayer(VideoInfo videoInfo) {
        String fingerprint = videoInfo.getFingerprint();
        release(fingerprint);
        players.put(fingerprint,new GiraffePlayer(videoInfo));
    }

    public void release(String fingerprint) {
        GiraffePlayer player = players.remove(fingerprint);
        if (player != null) {
            player.release();
        }

    }

    public void onPluginInit(PluginRegistry.Registrar registrar) {
        this.registrar=registrar;
    }

    public void onPluginDestroy() {
        for (Map.Entry<String, GiraffePlayer> p :players.entrySet()) {
            p.getValue().release();
        }
        players.clear();
    }

    public void removePlayer(String fingerprint) {
        players.remove(fingerprint);
    }
}
