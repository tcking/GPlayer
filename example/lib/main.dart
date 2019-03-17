import 'package:flutter/material.dart';
import 'package:gplayer/defaultmediacontrol.dart';
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
    player = GPlayer(
        uri: 'http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4',
        options: [Option(1, 'multiple_requests', 1)], 
        mediaController: DefaultMediaController(title: 'bunny bear'))
      ..init()
      ..addListener((_) {
        //update control button out of player
        setState(() {});
      });
  }

  @override
  void dispose() {
    player?.dispose();
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
        body: SingleChildScrollView(
          child: Padding(
            padding: EdgeInsets.only(top: 0),
            child: Column(
              children: <Widget>[
                AspectRatio(
                  aspectRatio: 16 / 9,
                  child: player.display,
                ),
                Padding(
                  padding: EdgeInsets.all(18),
                  child: Row(
                    children: <Widget>[
                      Text('landscapeWhenFullScreen:'),
                      Switch(
                          value: player.landscapeWhenFullScreen,
                          onChanged: (_) {
                            player.landscapeWhenFullScreen = _;
                            setState(() {
                              
                            });
                          })
                    ],
                  ),
                ),
                Padding(padding: EdgeInsets.all(18),child:
                  Row(children: <Widget>[
                    RaisedButton.icon(onPressed: (){
                      player?.mediaController?.control('toggleFloatWindow');
                    }, icon: Icon(Icons.featured_video), label: Text("toggle float window"))
                  ],)
                  ,)
              ],
            ),
          ),
        ),
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
