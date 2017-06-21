# gdx-gamesvcs

Framework and Implementation for using Game Services (BaaS) with libGDX.

## Supported game services

* [Google Play Games](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Google-Play-Games) (Android only, not yet open-sourced, but coming soon)
* [Newgrounds](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/Newgrounds) (HTML5-only)
* [GameJolt](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki/GameJolt) (all platforms)

Further contributes are very welcome!

## Motivation

When I wrote my first libGDX game, I realized there was no good library for Google Play Games (GPGS) integration available. So I implemented all the stuff myself. Luckily, because GPGS is basically Android-only, I kept things clean and implemented against an interface. Nevertheless, testing was a pain. I had no desktop implementation for the interface, so I always had to test on Android and check for `null` everywhere.

When I then published my HTML5 release to Newgrounds users complained that their highscores were not posted to the Newgrounds system. And they were right. That was the starting signal for me to feel in charge to make something.

## Basic concept

The library provides an interface `IGameServiceClient` that you reference in your core code. Your platform-dependant launchers instantiate an actual implementation of the interface.

Every implemented game service client has its own project you can decide to include or not. So your game won't get blown up with code you don't need.

There is a no-op implementation `NoGameServiceClient` provided that does absolutely nothing besides logging your calls. Use it to test platform-independant and to avoid `null` checks or NPEs.

See the corresponding demo app https://github.com/MrStahlfelge/gdx-gamesvcs-app for an example and this project's wiki for further documentation.

## Working demos

* [Newgrounds HTML5 demo app](http://www.newgrounds.com/projects/games/1110754/preview)
* [GameJolt HTML5 demo app](http://gamejolt.com/games/gdx-gamesvcs-gj/263351)
* Google Play Games: no published demo app, but my game [Falling Lightblocks](https://play.google.com/store/apps/details?id=de.golfgl.lightblocks&referrer=utm_source%3Dgh) is using this lib with GPGS

## Installation

At the moment, the library is unstable so you should consider to build it yourself. To do so, clone or download this repository then open it in Android Studio. Then perform the following command to compile and upload the library in your local repository:

    gradlew clean uploadArchives -PLOCAL=true
    
Without doing that, you will get my latest push to the public repository.

In any case, you can integrate the lib into your project by adding the dependencies to your `build.gradle` file.

Define the version of this API right after the gdxVersion: 
   
    gdxVersion = '1.9.6'
    gamsvcsVersion = '0.0.1-SNAPSHOT'

Core:

    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-desktop:$gamsvcsVersion"
    
Android:    
    
    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-android:$gamsvcsVersion"
    
and so on.

For HTML5, you also have to add a line to `GdxDefinition.gwt.xml` and `GdxDefinitionSuperdev.gwt.xml`:

    <inherits name="de.golfgl.gdxgamesvcs.gdx_gamesvcs_gwt" />

See [Demo App Commit 4ff746](https://github.com/MrStahlfelge/gdx-gamesvcs-app/commit/4ff746d591aead2a8ceeaff01c871209f31143cf) for the full list of dependencies.

Most platform sub projects are empty, but may be filled in the future. So better include them to avoid problems.

After including the dependencies and refreshing, you can use the `NoGameServiceClient` in your project. For using another Gameservice, add its dependencies according to its [wiki page](https://github.com/MrStahlfelge/gdx-gamesvcs/wiki)or implement your own client against `IGameServiceClient`.

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

Please note: It depends on the game service which calls are allowed without being connected to a user session.

### Submitting events and scores, unlocking achievements

You can feed your players by unlocking achievements and posting scores to leaderboards really easy:

    gsClient.submitToLeaderboard(leaderboardId, score, tag);
    
    gsClient.unlockAchievement(achievementId);
    
Events are interesting for you as a developer.     
    
    gsClient.submitEvent(eventId, 1);

Please note: It depends of the game services which calls are allowed for unauthenticated users. 

### Using cloud save feature

Not every game service and client implementation supports cloud save, so you must check the availability by checking

    if (gsClient.supportsCloudGameState() != CloudSaveCapability.NotSupported)

If you ensured that cloud save feature is enabled, use this methods to invoke it:    

    gsClient.loadGameState(fileId);

    gsClient.saveGameState(fileId, gameState, progressValue);

The methods perform an ansynchronous operation and call your listener afterwards.

    

## Updates & News
Follow me to receive release updates about this

https://twitter.com/MrStahlfelge

# License

The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
