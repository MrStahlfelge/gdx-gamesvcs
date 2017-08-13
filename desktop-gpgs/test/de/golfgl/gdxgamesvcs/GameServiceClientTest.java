package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import de.golfgl.gdxgamesvcs.IGameServiceClientEx.GameServiceFeature;

abstract public class GameServiceClientTest<T extends IGameServiceClientEx> extends Game
{
	private static final String TAG = "GameServiceClientTest";
	
	protected final T gsClient;
	
	private Stage stage;
	private Skin skin;
	
	private Preferences preferences;
	private Label connectedStatus;
	private Label connectionPendingStatus;
	private Label displayNameStatus;
	private TextField achievementOrEventId;
	private TextField gameId;
	private TextField gameDataToSave;
	private Label gameDataLoaded;
	private TextField leaderboardId;
	private TextField leaderboardScore;
	
	private FallbackUIExample fallbackUI;

	public GameServiceClientTest(T gsClient) {
		this.gsClient = gsClient;
	}
	
	abstract protected void createServiceSpecificInitialization(Table table);
	
	@Override
	public void create() 
	{
		skin = new Skin(Gdx.files.classpath("uiskin.json"));
		stage = new Stage(new ScreenViewport());
		Gdx.input.setInputProcessor(stage);
		
		Table table = new Table(skin);
		table.setFillParent(true);
		stage.addActor(table);
		
		fallbackUI = new FallbackUIExample(stage, skin, gsClient);
		
		gsClient.setListener(new GameServiceSafeListener(new IGameServiceListener() {
			
			@Override
			public void gsGameStateLoaded(byte[] gameState) {
				if(gameState != null){
					gameDataLoaded.setText("game state loaded : " + gameState.length + " bytes");
				}else{
					gameDataLoaded.setText("game state loading failed");
				}
			}
			
			@Override
			public void gsErrorMsg(GsErrorType et, String msg) {
				Gdx.app.error(TAG, et.toString() + " : " + msg);
			}
			
			@Override
			public void gsDisconnected() {
				Gdx.app.log(TAG, "disconnected");
				if(!gsClient.isConnected()) displayNameStatus.setText("");
			}
			
			@Override
			public void gsConnected() {
				Gdx.app.log(TAG, "connected");
				displayNameStatus.setText(gsClient.getPlayerDisplayName());
			}
		}));
		
		preferences = Gdx.app.getPreferences("gdx-gamesvcs-desktop-gpgs");
		
		// Connector initialization
		
		createTitle(table, "Connector Information");
		
		createInfo(table, "GameServiceId", gsClient.getGameServiceId());
		createInfo(table, "providesAchievementsUI", String.valueOf(gsClient.providesAchievementsUI()));
		createInfo(table, "providesLeaderboardUI", String.valueOf(gsClient.providesLeaderboardUI()));
		createInfo(table, "supportsCloudGameState", String.valueOf(gsClient.supportsCloudGameState()));
		
		createTitle(table, "Service Specific Initialization");
		
		createServiceSpecificInitialization(table);
		

		createTitle(table, "User connection/disconnection");
		
		createAction(table, "connect", new Runnable() {
			@Override
			public void run() {
				gsClient.connect(false);
			}
		});
		
		connectedStatus = createStatus(table, "isConnected");
		connectionPendingStatus = createStatus(table, "isConnectionPending");
		
		displayNameStatus = createStatus(table, "Player");
		
		createAction(table, "disconnect", new Runnable() {
			@Override
			public void run() {
				gsClient.disconnect();
			}
		});
		
		createAction(table, "logOff", new Runnable() {
			@Override
			public void run() {
				displayNameStatus.setText("");
				gsClient.logOff();
			}
		});

		
		createTitle(table, "Achievements");
		
		createAction(table, "showAchievements", new Runnable() {
			@Override
			public void run() {
				try {
					if(gsClient.providesAchievementsUI()){
						gsClient.showAchievements();
					}else{
						fallbackUI.showAchievements();
					}
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		
		achievementOrEventId = createField(table, "ach", "Achievement/Event ID", "");
		
		createAction(table, "unlockAchievement", new Runnable() {
			@Override
			public void run() {
				gsClient.unlockAchievement(achievementOrEventId.getText());
			}
		});
		
		createAction(table, "incrementAchievement by 1", new Runnable() {
			@Override
			public void run() {
				gsClient.incrementAchievement(achievementOrEventId.getText(), 1, 0);
			}
		});
		
		createAction(table, "submitEvent (increment by 1)", new Runnable() {
			@Override
			public void run() {
				gsClient.submitEvent(achievementOrEventId.getText(), 1);
			}
		});
		
		
		createTitle(table, "Leaderboards");
		
		leaderboardId = createField(table, "leaderboard.id", "Leaderboard ID", "");
		
		createAction(table, "showLeaderboards", new Runnable() {
			@Override
			public void run() {
				try {
					if(gsClient.providesLeaderboardUI()){
						gsClient.showLeaderboards(leaderboardId.getText());
					}else{
						fallbackUI.showLeaderboards(leaderboardId.getText());
					}
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		
		leaderboardScore = createField(table, "leaderboard.score", "Leaderboard Score", "");
		
		createAction(table, "submitToLeaderboard", new Runnable() {
			@Override
			public void run() {
				gsClient.submitToLeaderboard(leaderboardId.getText(), Long.valueOf(leaderboardScore.getText()), null);
			}
		});
		
		
		createTitle(table, "Saved Games");
		
		gameId = createField(table, "game.id", "Game ID", "test.dat");
		gameDataToSave = createField(table, "game.data", "Game Data to save (as text)", "{level: 5, life:2}");
		
		createAction(table, "showGameStates", new Runnable() {
			@Override
			public void run() {
				if(gsClient.isFeatureSupported(GameServiceFeature.gameStatesUI)){
					gsClient.showGameStates();
				}else{
					fallbackUI.showGameStates();
				}
			}
		});
		
		createAction(table, "saveGameState", new Runnable() {
			@Override
			public void run() {
				try {
					gsClient.saveGameState(gameId.getText(), gameDataToSave.getText().getBytes(), 0);
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		createAction(table, "loadGameState", new Runnable() {
			@Override
			public void run() {
				try {
					gameDataLoaded.setText("Loading...");
					gsClient.loadGameState(gameId.getText());
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		createAction(table, "deleteGameState", new Runnable() {
			@Override
			public void run() {
				gsClient.deleteGameState(gameId.getText());
			}
		});
		

		gameDataLoaded = createStatus(table, "Game Data loaded");
		
	}
	
	protected TextField createField(Table table, final String key, String name, String defValue){
		
		final TextField field = new TextField(preferences.getString(key, defValue), skin);
		table.add(name);
		table.add(field).expandX().fill().row();
		field.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				preferences.putString(key, field.getText());
			}
		});
		return field;
	}
	
	protected Label createStatus(Table table, String name){
		
		final Label label = new Label("", skin);
		label.setAlignment(Align.center);
		table.add(name);
		table.add(label).expandX().fill().row();
		return label;
	}
	protected Label createInfo(Table table, String name, String value){
		
		final Label label = new Label(value, skin);
		label.setAlignment(Align.center);
		table.add(name);
		table.add(label).expandX().fill().row();
		return label;
	}
	
	protected Label createTitle(Table table, String title){
		Table t = new Table(skin);
		t.setBackground("default-pane");
		table.add(t).expandX().fill().colspan(2).row();
		final Label label = new Label(title, skin);
		label.setAlignment(Align.center);
		t.add(label);
		return label;
	}

	
	protected void createAction(Table table, String name, final Runnable runnable){
		TextButton btConnect = new TextButton(name, skin);
		table.add(name);
		table.add(btConnect).row();
		
		btConnect.addListener(new ChangeListener() {

			@Override
			public void changed(ChangeEvent event, Actor actor) {
				try{
					runnable.run();
				}catch(Throwable e){
					Gdx.app.error(TAG, "runtime error", e);
				}
			}
		});
	}
	
	@Override
	public void render() {
		
		connectedStatus.setText(String.valueOf(gsClient.isConnected()));
		connectionPendingStatus.setText(String.valueOf(gsClient.isConnectionPending()));
		
		stage.act();
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		stage.draw();
	}
	
	@Override
	public void resize(int width, int height) {
		stage.getViewport().update(width, height, true);
	}
	
	@Override
	public void dispose() {
		preferences.flush();
	}
	
}