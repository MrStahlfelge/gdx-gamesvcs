package de.golfgl.gdxgamesvcs;

/**
 * Listener interface for Game Services
 * <p>
 * There is no guarantee that these methods are called on the render thread! Use Gdx.app.postRunnable when necessary!
 * <p>
 * Created by Benjamin Schulte on 26.03.2017.
 */

public interface IGameServiceListener {

    /**
     * Called when game service is sucessfully connected
     */
    public void gsConnected();

    /**
     * Called when game service has disconnected
     */
    public void gsDisconnected();

    public void gsErrorMsg(String msg);

    /**
     * Returns a game state that was saved in Cloud services
     *
     * @param gameState null if loading failed
     */
    public void gsGameStateLoaded(byte[] gameState);

}