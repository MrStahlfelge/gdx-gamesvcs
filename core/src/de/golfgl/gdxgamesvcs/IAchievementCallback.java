package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;

/**
 * Callback for {@link IGameServiceClientEx#fetchAchievements(boolean, IAchievementCallback)}
 * 
 * @author mgsx
 *
 */
public interface IAchievementCallback {
	/**
	 * Called in GLThread when achievements received.
	 * @param achievements null if achievements couldn't be fetched.
	 */
	void onAchievementsResponse(Array<Achievement> achievements);
}
