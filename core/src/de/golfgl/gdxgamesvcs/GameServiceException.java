package de.golfgl.gdxgamesvcs;

/**
 * Created by Benjamin Schulte on 26.03.2017.
 */

public class GameServiceException extends Throwable {

    public static class NotSupportedException extends GameServiceException {

    }

    public static class NoSessionException extends GameServiceException {

    }

}
