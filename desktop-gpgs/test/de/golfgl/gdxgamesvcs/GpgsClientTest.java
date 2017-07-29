package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;


public class GpgsClientTest extends GameServiceClientTest<GpgsClient>
{
	public static void main(String[] args) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.width = 800;
		config.height = 950;
		new LwjglApplication(new GpgsClientTest(), config);
	}
	
	private TextField appName;
	private TextField clientSecretPath;

	public GpgsClientTest() {
		super(new GpgsClient());
	}
	
	@Override
	protected void createServiceSpecificInitialization(Table table) 
	{
		appName = createField(table, "app.name", "Application Name", "");
		
		clientSecretPath = createField(table, "client.secret.path", "Client Secret Path", 
						Gdx.files.absolute(System.getProperty("user.home")).child("client_secret.json").path());
		
		createAction(table, "initialize", new Runnable() {
			@Override
			public void run() {
				gsClient.initialize(appName.getText(), Gdx.files.absolute(clientSecretPath.getText()));
			}
		});
	}
	
}
