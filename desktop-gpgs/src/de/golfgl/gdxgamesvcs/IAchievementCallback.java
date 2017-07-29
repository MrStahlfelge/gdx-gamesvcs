package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.utils.Array;

public interface IAchievementCallback {
	/**
	 * Called when achievements received.
	 * @param achievements null if achievements couldn't be fetched.
	 */
	void onAchievementsResponse(Array<Achievement> achievements);
}
