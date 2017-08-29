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
     * Called when game service user session is sucessfully connected
     */
    public void gsOnSessionActive();

    /**
     * Called when game service user session has disconnected or a connection attempt failed
     */
    public void gsOnSessionInactive();

    /**
     * Called from GameServiceClient to show a message to the user.
     *
     * @param et  error type for your own message
     * @param msg further information, may be null
     * @param t   Throwable causing the problem, may be null
     */
    public void gsShowErrorToUser(GsErrorType et, String msg, Throwable t);

    public enum GsErrorType {errorLoginFailed, errorUnknown, errorServiceUnreachable, errorLogoutFailed}
}
