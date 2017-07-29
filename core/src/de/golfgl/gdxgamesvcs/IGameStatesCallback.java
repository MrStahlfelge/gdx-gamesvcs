package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;

/**
 * Callback for {@link IGameServiceClientEx#fetchGameStates(IGameStatesCallback)}
 * 
 * @author mgsx
 *
 */
public interface IGameStatesCallback {
	/**
	 * Called in GLThread when game states list is received.
	 * @param gameStates null if game states couldn't be fetched.
	 */
	void onGameStatesResponse(Array<String> gameStates);
}
