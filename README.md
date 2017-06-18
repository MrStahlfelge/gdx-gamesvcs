# gdx-gamesvcs

Framework and Implementation for using Game Services (BaaS) with libGDX.

## Motivation

When I wrote my first libGDX game, I realized there was no good library for Google Play Games (GPGS) integration available. So I implemented all the stuff myself. Luckily, because GPGS is basically Android-only, I kept things clean and implemented against an interface. Nevertheless, testing was a pain. I had no desktop implementation for the interface, so I always had to test on Android and check for `null` everywhere.

When I then published my HTML5 release to Newgrounds users complained that their highscores were not posted to the Newgrounds system. And they were right. That was the starting signal for me to feel in charge to make something.

## Basic concept

The library provides an interface `IGameServiceClient` that you reference in your core code. Your platform-dependant launchers instantiate an actual implementation of the interface.

Every implemented game service client has its own project you can decide to include or not. So your game won't get blown up with code you don't need.

There is a no-op implementation `NoGameServiceClient` provided that does absolutely nothing besides logging your calls. Use it to test platform-independant and to avoid `null` checks or NPEs.

See the corresponding demo app https://github.com/MrStahlfelge/gdx-gamesvcs-app for an example and this project's wiki for further documentation.

## Supported game services

* Newgrounds (HTML5-only)
* Google Play Games (Android only, not open-sourced, coming soon)

Contributes are very welcome!

## Working demos

* [Newgrounds HTML5 demo app](http://www.newgrounds.com/projects/games/1110754/preview)
* Google Play Games: no open sourced app, but my game [Falling Lightblocks](https://play.google.com/store/apps/details?id=de.golfgl.lightblocks&referrer=utm_source%3Dgh) is using this lib with GPGS

## Installation

At the moment, the library is unstable and thus in no central repository. To integrate it in your libGDX project, clone or download this repository and open it in Android Studio. Then perform the following command to compile and upload the library in your local repository:

    gradlew clean uploadArchives -PLOCAL=true
    
Now you can integrate the lib into your project with by adding the dependencies to your `build.gradle` file.

Core:

    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-desktop:0.0.1-SNAPSHOT"
    
Android:    
    
    compile "de.golfgl.gdxgamesvcs:gdx-gamesvcs-android:0.0.1-SNAPSHOT"
    
and so on.

For HTML5, you have also add a line to `GdxDefinition.gwt.xml` and `GdxDefinitionSuperdev.gwt.xml`:

    <inherits name="de.golfgl.gdxgamesvcs.gdx_gamesvcs_gwt" />

See [Demo App Commit 4ff746](https://github.com/MrStahlfelge/gdx-gamesvcs-app/commit/4ff746d591aead2a8ceeaff01c871209f31143cf) for the full list of dependencies.

Most of the dependencies are empty, but may be filled in the future.

You no can use the `NoGameServiceClient` in your project. For using another Gameservice, add its dependencies according to its wiki page or implement your own client.

## Usage

A good library should be easy to use (let me know what you think of this lib). You should be fine by adding the following lines to your game in order to connect to the service:

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

        NgioClient gsClient = new NgioClient();

        gsClient.initialize( game service dependant initialization parameters );

        myGdxGame.gsClient = gsClient;


Check for `gsClient.isConnected()` if you successfully established a connection.

When connected, you can feed your players by unlocking achievements and posting scores to leaderboards really easy:

    gsClient.submitToLeaderboard(leaderboardId, score, tag);
    
    gsClient.unlockAchievement(achievementId);
    
    gsClient.submitEvent(eventId, 1);
    

## Updates & News
Follow me to receive release updates about this

https://twitter.com/MrStahlfelge

# License

The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
