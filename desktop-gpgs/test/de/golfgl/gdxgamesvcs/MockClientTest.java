package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;

import de.golfgl.gdxgamesvcs.achievement.Achievement;
import de.golfgl.gdxgamesvcs.leaderboard.LeaderBoardEntry;


public class MockClientTest extends GameServiceClientTest<MockGameServiceClient>
{
	public static void main(String[] args) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 950;
		
		// 2 second latency
		MockGameServiceClient client = new MockGameServiceClient(2) {
				
			@Override
			public boolean isFeatureSupported(GameServiceFeature feature) {
				switch(feature){
				case ShowAchievementsUI:
				case ShowLeaderboardUI:
				case ShowAllLeaderboardsUI:
					return false;
				default:
					return true;
				}
			}
			
			@Override
			protected String getPlayerName() {
				return "My Mock User";
			}
			
			@Override
			protected Array<LeaderBoardEntry> getLeaderbaordEntries() {
				
				return new Array<LeaderBoardEntry>(new LeaderBoardEntry[]{
					new LeaderBoardEntry(){{
						scoreRank = "1st";
						formattedValue = "100,000";
						userDisplayName = "GameMaster";
						userId = "1";
					}},
					new LeaderBoardEntry(){{
						scoreRank = "2nd";
						formattedValue = "30,000";
						userDisplayName = getPlayerName();
						userId = "2";
						currentPlayer = true;
					}},
					new LeaderBoardEntry(){{
						scoreRank = "3rd";
						formattedValue = "20";
						userDisplayName = "Losser";
						userId = "3";
					}}
				});
			}
			
			@Override
			protected Array<String> getGameStates() {
				return new Array<String>(new String[]{
						"game state 1", 
						"game state 2",
						"game state 3"});
			}
			
			@Override
			protected byte[] getGameState() {
				return "test data content".getBytes();
			}
			
			@Override
			protected Array<Achievement> getAchievements() {
				return new Array<Achievement>(new Achievement[]{
					new Achievement(){{
						achievementId = "1";
						completionPercentage = 1f;
						description = "The first achivement";
						title = "Achievement 1";
						iconUrl = null;
					}},
					new Achievement(){{
						achievementId = "2";
						completionPercentage = 0.33f;
						description = "The second achivement";
						title = "Achievement 2";
						iconUrl = null;
					}},
					new Achievement(){{
						achievementId = "3";
						completionPercentage = 0f;
						description = "The third achivement";
						title = "Achievement 3";
						iconUrl = null;
					}}
				});
			}
		};
	
		
		new LwjglApplication(new MockClientTest(client), config);
	}
	
	public MockClientTest(MockGameServiceClient gsClient) {
		super(gsClient);
	}

	@Override
	protected void createServiceSpecificInitialization(Table table) {
		// no initialization required for the mock
	}
	
}
