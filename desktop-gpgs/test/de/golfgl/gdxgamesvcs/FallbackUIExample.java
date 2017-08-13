package de.golfgl.gdxgamesvcs;

import java.io.IOException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;

import de.golfgl.gdxgamesvcs.LeaderBoard.Score;

/**
 * Here is an example of fallback UI.
 * 
 * It demonstrate how to implements non native features like : 
 * <ul>
 * <li>{@link IGameServiceClient#showAchievements()}</li>
 * <li>{@link IGameServiceClient#showLeaderboards(String)}</li>
 * <li>{@link IGameServiceClientEx#showGameStates()}</li>
 * </ul>
 * 
 * @author mgsx
 * 
 */
public class FallbackUIExample
{
	final private Stage stage;
	final private Skin skin;
	final private IGameServiceClientEx gsClient;
	
	private Table popup;
	
	final private ObjectMap<String, Texture> textureCache = new ObjectMap<String, Texture>();
	final private ObjectMap<String, Pixmap> pixmapCache = new ObjectMap<String, Pixmap>();
	
	public FallbackUIExample(Stage stage, Skin skin, IGameServiceClientEx gsClient) {
		super();
		this.stage = stage;
		this.skin = skin;
		this.gsClient = gsClient;
	}
	
	private void showWait(){
		popup = new Table(skin);
		popup.setFillParent(true);
		popup.setTouchable(Touchable.enabled);
		popup.setBackground("default-pane");
		popup.add(new Label("", skin){
			private float time;
			@Override
			public void act(float delta) {
				time += delta;
				setText("Please wait ... " + MathUtils.floor(time * 10)/10f);
				super.act(delta);
			}
		});
		stage.addActor(popup);
	}
	private void showError(){
		popup.reset();
		Table table = new Table(skin);
		popup.add(table);
		
		table.add("Sorry, an error occured, please retry later...").row();
		
		TextButton btClose = new TextButton("OK", skin);
		table.add(btClose).expandX().center();
		
		btClose.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				dismiss();
			}
		});
	}
	private void dismiss(){
		popup.remove();
		
		for(Texture texture : textureCache.values()){
			texture.dispose();
		}
		textureCache.clear();
		
		for(Pixmap pixmap : pixmapCache.values()){
			pixmap.dispose();
		}
		pixmapCache.clear();
		
		popup = null;
	}
	
	private Texture getTexture(String url){
		Texture texture = textureCache.get(url);
		if(texture == null){
			Pixmap pixmap = pixmapCache.get(url);
			if(pixmap != null){
				texture = new Texture(pixmap);
			}
		}
		return texture;
	}
	
	private void loadPixmap(String url){
		try {
			if(url != null){
				pixmapCache.put(url, DownloadUtil.downloadImage(url));
			}
		} catch (IOException e) {
			throw new GdxRuntimeException("Failed to download icon " + String.valueOf(url), e);
		}
	}
	
	public void showLeaderboards(final String leaderBoardId) throws GameServiceException {
		showWait();
		gsClient.fetchLeaderboard(leaderBoardId, false, false, new ILeaderBoardCallback() {
			@Override
			public void onLeaderBoardResponse(final LeaderBoard leaderBoard) {
				// download some icons
				if(leaderBoard != null){
					loadPixmap(leaderBoard.iconUrl);
					for(Score score : leaderBoard.scores){
						loadPixmap(score.avatarUrl);
					}
				}
				Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run() {
						if(leaderBoard != null){
							showLeaderboardsGUI(leaderBoard);
						}else{
							showError();
						}
					}
				});
			}
		});
	}
	
	private void showLeaderboardsGUI(LeaderBoard lb){
		
		final Table table = new Table(skin);
		table.defaults().pad(1, 5, 1, 5);
		
		popup.reset();
		popup.add(new ScrollPane(table, skin));
		
		TextButton btClose = new TextButton("Close", skin);
		table.add(btClose).center().colspan(4).row();
		
		// leader board header
		Image image = new Image(getTexture(lb.iconUrl));
		image.setScaling(Scaling.fit);
		table.add(image).size(32);
		
		table.add(lb.name).colspan(3);
		table.row();
		
		// leaderboard table header
		table.add("Player").colspan(2);
		table.add("Rank");
		table.add("Score");
		table.row();
		
		// leaderboard table body
		for(Score score : lb.scores){
			
			if(score.avatarUrl != null){
				Image avatar = new Image(getTexture(score.avatarUrl));
				avatar.setScaling(Scaling.fit);
				table.add(avatar).size(32);
			}else{
				table.add().size(32);
			}
			
			Label name = new Label(score.name, skin);
			if(score.currrentPlayer){
				name.setColor(Color.RED);
			}
			
			table.add(name);
			table.add(score.rank);
			table.add(score.score);
			table.row();
		}
		
		btClose.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				dismiss();
			}
		});
	}
	
	public void showAchievements() {
		showWait();
		gsClient.fetchAchievements(new IAchievementCallback() {
			@Override
			public void onAchievementsResponse(final Array<Achievement> achievements) {
				// download some icons
				if(achievements != null){
					for(Achievement a : achievements){
						loadPixmap(a.iconUrl);
					}
				}
				Gdx.app.postRunnable(new Runnable() {
					
					@Override
					public void run() {
						if(achievements != null){
							showAchievementsGUI(achievements);
						}else{
							showError();
						}
					}
				});
			}
		});
	}
	
	private void showAchievementsGUI(Array<Achievement> achievements){
		
		final Table table = new Table(skin);
		popup.reset();
		
		ScrollPane scroll = new ScrollPane(table, skin);
		
		popup.add(scroll).expand(false, false).top();
		
		TextButton btClose = new TextButton("Close", skin);
		table.add(btClose).center().colspan(4).row();
		table.defaults().pad(1, 5, 1, 5);
		
		for(Achievement a : achievements){
			
			Image image = new Image(getTexture(a.iconUrl));
			image.setScaling(Scaling.fit);
			
			table.add(image).size(32);
			table.add(a.name);
			table.add(a.description);
			
			Label statusLabel = new Label("", skin);
			statusLabel.setAlignment(Align.right);
			statusLabel.setColor(a.progress < 100 ? Color.GRAY : Color.GREEN);
			statusLabel.setText(a.progress + " %");
			table.add(statusLabel);
			
			table.row();
		}
		
		btClose.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				dismiss();
			}
		});
	}
	
	public void showGameStates(){
		showWait();
		gsClient.fetchGameStates(new IGameStatesCallback() {
			@Override
			public void onGameStatesResponse(final Array<String> gameStates) {
				Gdx.app.postRunnable(new Runnable() {
					@Override
					public void run() {
						if(gameStates != null){
							showGameStatesGUI(gameStates);
						}else{
							showError();
						}
					}
				});
			}
		});
	}
	
	private void showGameStatesGUI(Array<String> gameStates) {
		final Table table = new Table(skin);
		table.defaults().center().pad(1, 5, 1, 5);
		popup.reset();
		popup.add(new ScrollPane(table, skin));
		
		TextButton btClose = new TextButton("Close", skin);
		table.add(btClose).row();
		
		for(String gameState : gameStates){
			table.add(gameState).row();
		}
		
		btClose.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				dismiss();
			}
		});
	}
	
}
