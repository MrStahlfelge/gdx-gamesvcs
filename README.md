# gdx-gamesvcs

Framework and implementations for using Game Services (BaaS) with libGDX.

![Demo app](/assets/gdxgsgpgs.gif?raw=true "Demo app")

## Supported game services

* [Google Play Games](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Google-Play-Games) (Android and Desktop)
* [Amazon GameCircle](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Amazon-GameCircle) (Android)
* [Kongregate](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Kongregate) (HTML5)
* [GameJolt](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/GameJolt) (all platforms)
* [Newgrounds](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Newgrounds) (HTML5)

Further contributes are very welcome! Very wanted: Steam, Apple GameCenter. :-)

## Motivation

When I wrote my first libGDX game, I realized there was no good library for Google Play Games (GPGS) integration available. So I implemented all the stuff myself. Luckily, because GPGS is basically Android-only, I kept things clean and implemented against an interface. Nevertheless, testing was a pain. I had no desktop implementation for the interface, so I always had to test on Android and check for `null` everywhere.

When I then published my HTML5 release, users complained that their highscores were not posted to the hosting site's highscore system. Not to mention releasing to the Amazon App Store where GameCircle support is needed. So I felt in charge to do something.

## Basic concept

The library provides an interface `IGameServiceClient` that you reference in your core code. Your platform-dependant launchers instantiate an actual implementation of the interface.

Every implemented game service client has its own project you can decide to include or not. So your game won't get blown up with code you don't need.

There is a no-op implementation `NoGameServiceClient` provided that does absolutely nothing besides logging your calls. Use it to test platform-independant and to avoid `null` checks or NPEs.

See the corresponding demo app https://github.com/MrStahlfelge/gdx-gamesvcs-app for an example and this project's wiki for further documentation.

## Working demos

* Google Play Games: no published demo app, but my game [Falling Lightblocks](https://play.google.com/store/apps/details?id=de.golfgl.lightblocks&referrer=utm_source%3Dgh) is using this lib with GPGS
* Amazon GameCircle: no published demo app, but my game [Falling Lightblocks](https://www.amazon.com/gp/mas/dl/android?p=de.golfgl.lightblocks) is using this lib with GameCircle.
* [GameJolt HTML5 demo app](http://gamejolt.com/games/gdx-gamesvcs-gj/263351)
* [Newgrounds HTML5 demo app](http://www.newgrounds.com/projects/games/1110754/preview)

## Installation

This project is published to the Sonatype Maven repository. You can integrate the lib 
into your project by just adding the dependencies to your `build.gradle` file.

Define the version of this API right after the gdxVersion: 
   
    gdxVersion = '1.9.6'
    gamesvcsVersion = '0.1.1'

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

A good library should be easy to use (let me know what you think of this lib). 

### Connecting to the game service

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
        gsClient.connect(true);
        
    }
    
    @Override
    public void pause() {
        super.pause();

        gsClient.disconnect();
    }

    @Override
    public void resume() {
        super.resume();

        gsClient.connect(true);
    }

In the launcher class you instantiate and initialize the GameServiceClient you really want to use:

        YourGameserviceClient gsClient = new YourGameserviceClient();

        gsClient.initialize( game service dependant initialization parameters );

        myGdxGame.gsClient = gsClient;


Check for `gsClient.isConnected()` if you successfully established a connection, or set a listeneder and wait for the call to `gsConnected`.

### Submitting events and scores, unlocking achievements

You can feed your players by unlocking achievements and posting scores to leaderboards really easy:

    gsClient.submitToLeaderboard(leaderboardId, score, tag);
    
    gsClient.unlockAchievement(achievementId);
    
Events are interesting for you as a developer.     
    
    gsClient.submitEvent(eventId, 1);

Please note: It depends of the game services which calls are allowed for unauthenticated users. The API client implementations deal with that so you don't have to.  

### Cloud save

Not every game service and client implementation supports cloud save, so you must check the availability by checking

    if (gsClient.supportsCloudGameState() != CloudSaveCapability.NotSupported)

If you ensured that cloud save feature is enabled, use this methods to invoke it:    

    gsClient.loadGameState(fileId);

    gsClient.saveGameState(fileId, gameState, progressValue);

The methods perform an ansynchronous operation and call your listener afterwards.


### Fetching scores and achievement status

The interface provides a method for open up an API's default leaderboard or achievmeent UI:

    gsClient.providesAchievementsUI();
    gsClient.showAchievements();
    // same for leaderboards

At the moment, such a default UI is only provided by Google Play Games and GameCircle on Android.

Fetching scores and achievement status to show in your own UI is work in progress in v0.2.x.
    

## Updates & News
Follow me to receive release updates about this

https://twitter.com/MrStahlfelge

# License

The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
