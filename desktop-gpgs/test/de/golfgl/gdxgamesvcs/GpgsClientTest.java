package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
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

// TODO wrap every action calls within a thread ?!
public class GpgsClientTest extends Game
{
	private static final String TAG = "GpgsClientTest";
	
	private static void background(Runnable runnable){
		new Thread(runnable).start();
	}
	
	public static void main(String[] args) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 800;
		new LwjglApplication(new GpgsClientTest(), config);
	}
	
	private Stage stage;
	private Skin skin;
	
	private TextField appName;
	private TextField clientSecretPath;
	private Preferences preferences;
	private Label connectedStatus;
	private GpgsClient gpgsClient;
	private Label connectionPendingStatus;
	private Label displayNameStatus;
	private TextField achievementOrEventId;
	private TextField gameId;
	private TextField gameDataToSave;
	private Label gameDataLoaded;

	@Override
	public void create() {
		skin = new Skin(Gdx.files.classpath("uiskin.json"));
		stage = new Stage(new ScreenViewport());
		Gdx.input.setInputProcessor(stage);
		
		Table table = new Table(skin);
		table.setFillParent(true);
		stage.addActor(table);
		
		gpgsClient = new GpgsClient();
		
		gpgsClient.setListener(new GameServiceSafeListener(new IGameServiceListener() {
			
			@Override
			public void gsGameStateLoaded(byte[] gameState) {
				Gdx.app.log(TAG, "game loaded " + gameState.length + " bytes");
				gameDataLoaded.setText(new String(gameState));
			}
			
			@Override
			public void gsErrorMsg(GsErrorType et, String msg) {
				Gdx.app.error(TAG, et.toString() + " : " + msg);
			}
			
			@Override
			public void gsDisconnected() {
				Gdx.app.log(TAG, "disconnected");
				displayNameStatus.setText("");
			}
			
			@Override
			public void gsConnected() {
				Gdx.app.log(TAG, "connected");
				displayNameStatus.setText(gpgsClient.getPlayerDisplayName());
			}
		}));
		
		preferences = Gdx.app.getPreferences("gdx-gamesvcs-desktop-gpgs");
		
		// Connector initialization
		
		createTitle(table, "Connector Information");
		
		createInfo(table, "GameServiceId", gpgsClient.getGameServiceId());
		createInfo(table, "providesAchievementsUI", String.valueOf(gpgsClient.providesAchievementsUI()));
		createInfo(table, "providesLeaderboardUI", String.valueOf(gpgsClient.providesLeaderboardUI()));
		createInfo(table, "supportsCloudGameState", String.valueOf(gpgsClient.supportsCloudGameState()));
		
		
		appName = createField(table, "app.name", "Application Name", "");
		
		clientSecretPath = createField(table, "client.secret.path", "Client Secret Path", 
						Gdx.files.absolute(System.getProperty("user.home")).child("client_secret.json").path());
		
		createAction(table, "initialize", new Runnable() {
			@Override
			public void run() {
				gpgsClient.initialize(appName.getText(), Gdx.files.absolute(clientSecretPath.getText()));
			}
		});
		

		createTitle(table, "User connection/disconnection");
		
		createAction(table, "connect", new Runnable() {
			@Override
			public void run() {
				gpgsClient.connect(false);
			}
		});
		
		connectedStatus = createStatus(table, "isConnected");
		connectionPendingStatus = createStatus(table, "isConnectionPending");
		
		displayNameStatus = createStatus(table, "Player");
		
		createAction(table, "disconnect", new Runnable() {
			@Override
			public void run() {
				gpgsClient.disconnect();
			}
		});
		
		createAction(table, "logOff", new Runnable() {
			@Override
			public void run() {
				displayNameStatus.setText("");
				gpgsClient.logOff();
			}
		});

		
		createTitle(table, "Achievements / Leaderboard / Events");
		
		createAction(table, "showAchievements", new Runnable() {
			@Override
			public void run() {
				try {
					gpgsClient.showAchievements();
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		
		achievementOrEventId = createField(table, "ach", "Achievement/Event ID", "");
		
		createBgAction(table, "unlockAchievement", new Runnable() {
			@Override
			public void run() {
				gpgsClient.unlockAchievement(achievementOrEventId.getText());
			}
		});
		
		createBgAction(table, "incrementAchievement by 1", new Runnable() {
			@Override
			public void run() {
				gpgsClient.incrementAchievement(achievementOrEventId.getText(), 1, 0);
			}
		});
		
		createAction(table, "showLeaderboards", new Runnable() {
			@Override
			public void run() {
				try {
					gpgsClient.showLeaderboards("");
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		createBgAction(table, "submitEvent (increment by 1)", new Runnable() {
			@Override
			public void run() {
				gpgsClient.submitEvent(achievementOrEventId.getText(), 1);
			}
		});
		
		createTitle(table, "Saved Games");
		
		gameId = createField(table, "game.id", "Game ID", "test.dat");
		gameDataToSave = createField(table, "game.data", "Game Data to save (as text)", "{level: 5, life:2}");
		
		createBgAction(table, "saveGameState", new Runnable() {
			@Override
			public void run() {
				try {
					gpgsClient.saveGameState(gameId.getText(), gameDataToSave.getText().getBytes(), 0);
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		createBgAction(table, "loadGameState", new Runnable() {
			@Override
			public void run() {
				try {
					gpgsClient.loadGameState(gameId.getText());
				} catch (GameServiceException e) {
					Gdx.app.error(TAG, "API error", e);
				}
			}
		});
		gameDataLoaded = createStatus(table, "Game Data loaded");
		
	}
	
	private TextField createField(Table table, final String key, String name, String defValue){
		
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
	
	private Label createStatus(Table table, String name){
		
		final Label label = new Label("", skin);
		label.setAlignment(Align.center);
		table.add(name);
		table.add(label).expandX().fill().row();
		return label;
	}
	private Label createInfo(Table table, String name, String value){
		
		final Label label = new Label(value, skin);
		label.setAlignment(Align.center);
		table.add(name);
		table.add(label).expandX().fill().row();
		return label;
	}
	private Label createTitle(Table table, String title){
		Table t = new Table(skin);
		t.setBackground("default-pane");
		table.add(t).expandX().fill().colspan(2).row();
		final Label label = new Label(title, skin);
		label.setAlignment(Align.center);
		t.add(label);
		return label;
	}

	
	private void createAction(Table table, String name, final Runnable runnable){
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
	private void createBgAction(Table table, String name, final Runnable runnable){
		createAction(table, name, new Runnable() {
			@Override
			public void run() {
				background(runnable);
			}
		});
	}
	
	@Override
	public void render() {
		
		connectedStatus.setText(String.valueOf(gpgsClient.isConnected()));
		connectionPendingStatus.setText(String.valueOf(gpgsClient.isConnectionPending()));
		
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
