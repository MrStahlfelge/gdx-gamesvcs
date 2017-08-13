package de.golfgl.gdxgamesvcs.gamestate;

/**
 * Response listener for
 * {@link de.golfgl.gdxgamesvcs.IGameServiceClient#saveGameState(String, byte[], long, ISaveGameStateResponseListener)}
 * <p>
 * Created by Benjamin Schulte on 12.08.2017.
 */

public interface ISaveGameStateResponseListener {

    /**
     * Result of save game request
     *
     * @param success   true if game state was sucessfully saved
     * @param errorCode null of succesful
     */
    void onGameStateSaved(boolean success, String errorCode);
}
