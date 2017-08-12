package de.golfgl.gdxgamesvcs.gamestate;

/**
 * Called when a game state is loaded or loading failed when
 * {@link de.golfgl.gdxgamesvcs.IGameServiceClient#loadGameState(String, ILoadGameStateResponseListener)}
 * was performed.
 * <p>
 * Created by Benjamin Schulte on 12.08.2017.
 */

public interface ILoadGameStateResponseListener {
    /**
     * Returns a game state that was saved in Cloud services
     *
     * @param gameState null if loading failed
     */
    public void gsGameStateLoaded(byte[] gameState);
}
