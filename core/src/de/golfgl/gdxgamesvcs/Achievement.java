package de.golfgl.gdxgamesvcs;

/**
 * Achievement data
 * 
 * @author mgsx
 *
 */
public class Achievement 
{
	public String id;
	
	public String name;
	
	public String description;
	
	/** icon url, may varying depending on locking state */
	public String iconUrl;
	
	/**
	 * Progression rate in percents ranges from 0 to 100.
	 * For non incremental achievements (locked/unlocked), this value is 0 when locked, 100 when unlocked.
	 * Interger is used (in favor to float) to easily check if this achievement is completed (== 100)
	 */
	public int progress;
}
