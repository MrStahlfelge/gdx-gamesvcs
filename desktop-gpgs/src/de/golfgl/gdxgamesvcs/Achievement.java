package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.graphics.Pixmap;

public class Achievement 
{
	// definition part
	public String id, name, description;
	
	/** icon url, may varying depending on locking state */
	public String iconUrl;
	
	/** icon pixels or null if icon has not been fetched or achievement is hidden. */
	public Pixmap icon;
	
	public boolean isIncremental;
	
	public boolean unlocked;
	public boolean hidden;
	
	/** current step for incremental achievements, 0/1 for standard achievements based on lock state */
	public int currentSteps;
	
	/** total steps for incremental achievements, 1 for standard achievements */
	public int totalSteps;
}
