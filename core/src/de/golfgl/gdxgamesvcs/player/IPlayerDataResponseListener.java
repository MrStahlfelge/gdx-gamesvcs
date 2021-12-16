package de.golfgl.gdxgamesvcs.player;

import com.badlogic.gdx.utils.Array;

import de.golfgl.gdxgamesvcs.IGameServiceClient;

/**
 * Callback for
 * {@link IGameServiceClient#getPlayerData(IPlayerDataResponseListener)} 
 *
 * @author Kari Vatjus-Anttila
 */
public interface IPlayerDataResponseListener {
    /**
     * Called when player data is received.
     *
     * @param player Player object retrieved from a Game Service or null on failure
     */
    void onPlayerDataResponse(IPlayerData player);
}
