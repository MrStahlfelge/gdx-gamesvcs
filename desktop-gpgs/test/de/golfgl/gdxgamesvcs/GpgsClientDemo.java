package de.golfgl.gdxgamesvcs;

import java.io.IOException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;

/**
 * Here is an example of {@link GpgsClient} client override.
 * 
 * It demonstrate how to implements non native features like {@link IGameServiceClient#showAchievements()}
 * and {@link IGameServiceClient#showLeaderboards(String)} as well as .. TODO showSavedGames
 * 
 * @author mgsx
 *
 */
public class GpgsClientDemo extends GpgsClient
{
	private Stage stage;
	private Skin skin;
	private Table popup;
	
	public GpgsClientDemo(Stage stage, Skin skin) {
		super();
		this.stage = stage;
		this.skin = skin;
		
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
	
	@Override
	public boolean providesAchievementsUI() {
		return true;
	}
	
	@Override
	public void showAchievements() throws GameServiceException {
		showWait();
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				showAchievementsSync();
			}
		});
	}
	
	protected void showAchievementsSync() throws IOException 
	{
		final Array<Achievement> achievements = fetchAchievementsSync(true);
		
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				showAchievementsGUI(achievements);
			}
		});
	}
	
	private void showAchievementsGUI(Array<Achievement> achievements){
		
		final Array<Texture> textures = new Array<Texture>();
		
		final Table table = new Table(skin);
		popup.reset();
		
		ScrollPane scroll = new ScrollPane(table, skin);
		
		popup.add(scroll).expand(false, false).top();
		
		TextButton btClose = new TextButton("Close", skin);
		table.add(btClose).center().colspan(4).row();
		table.defaults().pad(1, 5, 1, 5);
		
		for(Achievement a : achievements){
			
			if(a.hidden) continue;
			
			Texture iconTexture = new Texture(a.icon);
			textures.add(iconTexture);
			Image image = new Image(iconTexture);
			image.setScaling(Scaling.fit);
			
			table.add(image).size(32);
			table.add(a.name);
			table.add(a.description);
			
			Label statusLabel = new Label("", skin);
			statusLabel.setColor(a.unlocked ? Color.GREEN : Color.GRAY);
			table.add(statusLabel);
			
			if(a.isIncremental) 
			{
				statusLabel.setText(a.currentSteps + " / " + a.totalSteps);
			}
			else
			{
				statusLabel.setText(a.unlocked ? "Unlocked" : "Locked");
			}
			
			table.row();
		}
		
		btClose.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				popup.remove();
				popup = null;
				for(Texture texture : textures){
					texture.dispose();
				}
			}
		});
	}
	
	public void showGameStates(){
		showWait();
		background(new SafeRunnable() {
			@Override
			public void run() throws IOException {
				showGameStatesSync();
			}
		});
	}
	
	private void showGameStatesSync() throws IOException{
		final Array<String> gameStates = listGamesSync();
		
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				showGameStatesGUI(gameStates);
			}
		});
	}

	protected void showGameStatesGUI(Array<String> gameStates) {
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
				popup.remove();
				popup = null;
			}
		});
	}
	
}
