# GPlayer

[![pub package](https://img.shields.io/pub/v/gplayer.svg)](https://pub.dartlang.org/packages/gplayer)

Video Player plugin for Flutter,On Android, the backing player is base on [ijkplayer 0.8.8](https://github.com/Bilibili/ijkplayer) （not implement on iOS）

![The example app running in Android](https://raw.githubusercontent.com/tcking/GPlayer/master/screencap/s1.gif)


## features
1. base on ijkplayer（ffmpeg）,support RTMP , HLS (http & https) , MP4,M4A etc.
2. gestures for volume control
3. gestures for brightness control
4. gestures for forward or backward
5. support fullscreen
6. try to replay when error(only for live video)
7. specify video scale type
8. support lazy load (download player on demand)
9. customize media controller (without change this project source code)



## Getting Started

### 1.add dependency
First, add `gplayer` as a [dependency in your pubspec.yaml file](https://flutter.io/using-packages/).

``` yaml
dependencies:
  flutter:
    sdk: flutter

  # add gplayer dependency
  gplayer: ^0.0.1
```

### 2.create player

``` dart
import 'package:flutter/material.dart';
import 'package:gplayer/gplayer.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  GPlayer player;
  @override
  void initState() {
    super.initState();
    //1.create & init player
    player = GPlayer(uri: 'http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4')
      ..init()
      ..addListener((_) {
        //update control button out of player
        setState(() {});
      });
  }

  @override
  void dispose() {
    player?.dispose(); //2.release player
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Video Demo',
      home: Scaffold(
        appBar: AppBar(
          title: Text('GPlayer'),
        ),
        body: player.display,//3.put the player display in Widget tree
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            setState(() {
              player.isPlaying ? player.pause() : player.start();
            });
          },
          child: Icon(
            player.isPlaying ? Icons.pause : Icons.play_arrow,
          ),
        ),
      ),
    );
  }
}

```
## Customize media contoller

1.define a class extend from `buildMediaController`

2.implement method `Widget buildMediaController(BuildContext context)`

3.pass the instance to player constructor `GPlayer(uri:'',mediaController:MyMeidaController())`