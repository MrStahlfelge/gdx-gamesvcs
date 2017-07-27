package de.golfgl.gdxgamesvcs;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.games.model.Player;

import de.golfgl.gdxgamesvcs.GameServiceException.NotSupportedException;
import de.golfgl.gdxgamesvcs.IGameServiceListener.GsErrorType;

public class GpgsClient implements IGameServiceClient
{

	private static final String ME = "me";

	protected IGameServiceListener gameListener; // TODO call
	
	private String userID = ME; // TODO uniq ?
	
	private boolean connected;
	
	private volatile boolean connecting;
	
	@Override
	public String getGameServiceId() {
		return IGameServiceClient.GS_GOOGLEPLAYGAMES_ID;
	}

	@Override
	public void setListener(IGameServiceListener gsListener) {
		gameListener = gsListener;
	}
	
	// TODO could not be a file
	public void initialize(String applicationName, FileHandle clientSecretFile){
		try {
			GAPIGateway.init(applicationName, clientSecretFile);
		} catch (GeneralSecurityException e) {
			throw new GdxRuntimeException(e);
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
	}
	
	@Override
	public boolean connect(boolean silent) {
		try {
			connecting = true;
			GAPIGateway.authorize(userID);
			connected = true;
			gameListener.gsConnected();
		} catch (Exception e) {
			gameListener.gsErrorMsg(GsErrorType.errorUnknown, "failed to connect");
			throw new GdxRuntimeException(e);
		} finally {
			connecting = false;
		}
		
		return false;
	}

	@Override
	public void disconnect() {
		// TODO add disconnect support ....
		
	}

	@Override
	public void logOff() {
		// TODO add logOff support ....
		
	}

	@Override
	public String getPlayerDisplayName() {
		// TODO cache the result upon connection !
		try {
			Player player = GAPIGateway.games.players().get(ME).execute();
			return player.getDisplayName();
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean isConnectionPending() {
		return connecting;
	}

	@Override
	public boolean providesLeaderboardUI() {
		return false;
	}

	@Override
	public void showLeaderboards(String leaderBoardId) throws GameServiceException {
		throw new NotSupportedException();
	}

	@Override
	public boolean providesAchievementsUI() {
		return false;
	}

	@Override
	public void showAchievements() throws GameServiceException {
		throw new NotSupportedException();
	}

	@Override
	public boolean submitToLeaderboard(String leaderboardId, long score, String tag) {
		try {
			GAPIGateway.games.scores().submit(leaderboardId, score).execute();
			return true; 
		} catch (IOException e) {
			Gdx.app.error("", "failed submit to leaderboard", e); // TODO TAG
		}
		return false;
	}

	@Override
	public boolean submitEvent(String eventId, int increment) {
		// TODO don't know the API ...
		return false;
	}

	@Override
	public boolean unlockAchievement(String achievementId) {
		try {
			GAPIGateway.games.achievements().unlock(achievementId).execute();
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
		return false;
	}

	@Override
	public boolean incrementAchievement(String achievementId, int incNum, float completionPercentage) {
		try {
			GAPIGateway.games.achievements().increment(achievementId, incNum).execute();
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
		return false;
	}

	@Override
	public void saveGameState(String fileId, byte[] gameState, long progressValue) throws GameServiceException {
		try {
			
			java.io.File file = java.io.File.createTempFile("games", "dat");
			new FileHandle(file).writeBytes(gameState, false);
			
			// no type since it is binary data
			FileContent mediaContent = new FileContent(null, file);
			
			// find file on server
			
			// TODO escape some chars (') see : https://developers.google.com/drive/v3/web/search-parameters#fn1
			List<File> files = GAPIGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + fileId + "'").execute().getFiles();
			if(files.size() > 1){
				throw new GdxRuntimeException("multiple files with name " + fileId + " exists.");
			}
			
			// file exists then update it
			if(files.size() > 0){
				
				File remoteFile = files.get(0);
				// just update content, leave metadata intact.
				
				GAPIGateway.drive.files().update(remoteFile.getId(), null, mediaContent).execute();
				
				Gdx.app.log("GAPI", "File updated ID: " + remoteFile.getId());
			}
			// file doesn't exists then create it
			else{
				File fileMetadata = new File();
				fileMetadata.setName(fileId);
				
				// app folder is a reserved keyyword for current application private folder.
				fileMetadata.setParents(Collections.singletonList("appDataFolder"));
				
				File remoteFile = GAPIGateway.drive.files().create(fileMetadata, mediaContent)
						.setFields("id")
						.execute();
				
				Gdx.app.log("GAPI", "File created ID: " + remoteFile.getId());
			}
			
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		}
	}

	@Override
	public void loadGameState(String fileId) throws GameServiceException {
		
		InputStream stream = null;
		try {
			// TODO refactor
			List<File> files = GAPIGateway.drive.files().list().setSpaces("appDataFolder").setQ("name='" + fileId + "'").execute().getFiles();
			if(files.size() > 1){
				throw new GdxRuntimeException("multiple files with name " + fileId + " exists.");
			}
			if(files.size() < 1){
				
			}
			else
			{
				File remoteFile = files.get(0);
				// note that size metadata can be null ...
				
				stream = GAPIGateway.drive.files().get(remoteFile.getId()).executeMediaAsInputStream();
			
				byte [] data = StreamUtils.copyStreamToByteArray(stream);
				
				gameListener.gsGameStateLoaded(data);
			}
		} catch (IOException e) {
			throw new GdxRuntimeException(e);
		} finally {
			StreamUtils.closeQuietly(stream);
		}
	}

	@Override
	public CloudSaveCapability supportsCloudGameState() {
		return CloudSaveCapability.MultipleFilesSupported;
	}

}
