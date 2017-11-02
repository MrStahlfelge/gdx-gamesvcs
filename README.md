# gdx-gamesvcs

Framework and implementations for using Game Services (BaaS) with libGDX.

![Demo app](/assets/gdxgsgpgs.gif?raw=true "Demo app")

## Supported game services

* [Google Play Games](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Google-Play-Games) (Android and Desktop)
* [Amazon GameCircle](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Amazon-GameCircle) (Android)
* [Kongregate](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Kongregate) (HTML5)
* [GameJolt](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/GameJolt) (all platforms)
* [Newgrounds](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Newgrounds) (HTML5)

Further contributes are very welcome! Very wanted: Steam, Apple GameCenter, Play Games HTML5. :-)

## Motivation

When I wrote my first libGDX game, I realized there was no good library for Google Play Games (GPGS) integration available. So I implemented all the stuff myself. Luckily, because GPGS is basically Android-only, I kept things clean and implemented against an interface. Nevertheless, testing was a pain. I had no desktop implementation for the interface, so I always had to test on Android and check for `null` everywhere.

When I then published my HTML5 release, users complained that their highscores were not posted to the hosting site's highscore system. Not to mention releasing to the Amazon App Store where GameCircle support is needed. So I felt in charge to do something.

## Basic concept

The library provides an interface `IGameServiceClient` that you reference in your core code. Your platform-dependant launchers instantiate an actual implementation of the interface.

Every implemented game service client has its own project you can decide to include or not. So your game won't get blown up with code you don't need.

There is a no-op implementation `NoGameServiceClient` provided that does absolutely nothing besides logging your calls. Use it to test platform-independant and to avoid `null` checks or NPEs.
For testing your UI's behaviour on slow callback responses, you can use `MockGameServiceClient`.

See the corresponding demo app https://github.com/MrStahlfelge/gdx-gamesvcs-app for an example and this project's wiki for further documentation.

## Working demos

* Google Play Games: my game [Falling Lightblocks](https://play.google.com/store/apps/details?id=de.golfgl.lightblocks&referrer=utm_source%3Dgh) is using this lib with GPGS
* Amazon GameCircle: my game [Falling Lightblocks](https://www.amazon.com/gp/mas/dl/android?p=de.golfgl.lightblocks) uses this lib with GameCircle.
* [Kongregate HTML5 demo app](http://www.kongregate.com/games/MrStahlfelge/kongregate-api-for-libgdx-example)
* [GameJolt HTML5 demo app](http://gamejolt.com/games/gdx-gamesvcs-gj/263351)
* [Newgrounds HTML5 demo app](http://www.newgrounds.com/projects/games/1110754/preview)

## Installation

This project is published to the Sonatype Maven repository. You can integrate the lib 
into your project by just adding the dependencies to your `build.gradle` file.

Define the version of this API right after the gdxVersion: 
   
    gdxVersion = '1.9.6'
    gamesvcsVersion = '0.2.0' // or '0.2.1-SNAPSHOT'

Core:

    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-core:$gamesvcsVersion"
    
For the HTML5 project, you also have to include the sources

    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-core:$gamesvcsVersion:sources"

and add a line to `GdxDefinition.gwt.xml` and `GdxDefinitionSuperdev.gwt.xml`:

    <inherits name="de.golfgl.gdxgamesvcs.gdx_gamesvcs_gwt" />

After including the dependencies and refreshing, you can use the `NoGameServiceClient` in your project. For using another Gameservice, add its dependencies according to its [wiki page](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki) or implement your own client against `IGameServiceClient`.

### Building from source
To build from source, clone or download this repository, then open it in Android Studio. Perform the following command to compile and upload the library in your local repository:

    gradlew clean uploadArchives -PLOCAL=true
    
See `build.gradle` file for current version to use in your dependencies.

## Usage
**This documentation is only valid for v0.2. If you want to use older version v0.1, please see documentation on release/0.1.x branch.**

When updating from v0.1 to v0.2, please note that main interface methods were 
renamed to clarify their purpose. See [issue #15](https://github.com/MrStahlfelge/gdx-gamesvcs/issues/15) for the full list.

### Initializing the game service client

You should be fine by adding the following lines to your game in order to connect to the service:

Main game class:

    public IGameServiceClient gsClient;
    
    @Override
    public void create() {
        // ...awesome initialization code...
        
        if (gsClient == null)
            gsClient = new NoGameServiceClient();

        // for getting callbacks from the client
        gsClient.setListener(this);

        // establish a connection to the game service without error messages or login screens
        gsClient.resumeSession();
        
    }
    
    @Override
    public void pause() {
        super.pause();

        gsClient.pauseSession();
    }

    @Override
    public void resume() {
        super.resume();

        gsClient.resumeSession();
    }

In the launcher class you instantiate and initialize the GameServiceClient you really want to use:

        YourGameserviceClient gsClient = new YourGameserviceClient();

        gsClient.initialize( game service dependant initialization parameters );

        myGdxGame.gsClient = gsClient;


If you want to know if you established a connection to a user session, you can use 
`gsClient.isSessionActive()`, or set a listener and wait for the call to 
`gsOnSessionActive()`. You don't need to check if a user session is active for 
submitting scores, events and unlocking achievements, as some game services allow anonymous
or guest submits. The API client implementations do all needed checks.

### Submitting events and scores, unlocking achievements

You can feed your players by unlocking achievements and posting scores to leaderboards really easy:

    gsClient.submitToLeaderboard(leaderboardId, score, tag);
    
    gsClient.unlockAchievement(achievementId);
    
Events are interesting for you as a developer.     
    
    gsClient.submitEvent(eventId, 1);

Please note: It depends of the game services which calls can be processed without a user session. The API client implementations deal with that so you don't have to.  

### Cloud save

Not every game service and client implementation supports cloud save, check the overview table in the wiki. In your game, you can and must check the availability by calling

    if (gsClient.isFeatureSupported(GameServiceFeature.GameStateStorage))

If you ensured that cloud save feature is available, use these methods to invoke it:    

    gsClient.loadGameState(fileId, new ILoadGameStateResponseListener() {...});

    gsClient.saveGameState(fileId, gameState, progressValue, 
                           new ISaveGameStateResponseListener() {...});

The methods perform an ansynchronous operation and call your listener afterwards.


### Fetching scores and achievement status

The interface provides a method for open up an API's default leaderboard or achievment UI:

    gsClient.providesAchievementsUI();
    gsClient.showAchievements();
    // same for leaderboards

At the moment, such a default UI is only provided by Google Play Games and GameCircle on Android, 
so you need to check with `gsClient.isFeatureSupported()` before calling. 

Fetching scores and achievement status to show in your own UI can be done by calling
     
     gsClient.fetchLeaderboardEntries(...)
     gsClient.fetchAchievements(...)
     
after checking
    
     gsClient.isFeatureSupported(GameServiceFeature.FetchLeaderBoardEntries)
     gsClient.isFeatureSupported(GameServiceFeature.FetchAchievements))

You give a listener as a parameter which will be called with a list of achievement or leader board 
entries in response. See to the JavaDocs or the demo application for more information.

### Explicit log in and out
Some game services support user sign in and out, some need the user to log in manually for the first time. Use the game service interface's `logIn()` and `logOut()` methods for doing so. These methods should only be called when the user manually requested an explicit log in/out. 

Note: For Google Play Games your game even *must* provide a sign in/out button to be considered Google Play Games Services-compatible.

## Updates & News
Follow me to receive release updates about this

https://twitter.com/MrStahlfelge

# License

The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
