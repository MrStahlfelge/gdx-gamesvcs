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
     * Called when game service has disconnected or a connection attempt failed
     */
    public void gsDisconnected();

    /**
     * Called from GameServiceClient to show a message to the user.
     *
     * @param et - error type for your own message
     * @param msg - further information, may be null
     */
    public void gsErrorMsg(GsErrorType et, String msg);

    public enum GsErrorType {errorLoginFailed, errorUnknown, errorServiceUnreachable}
}
