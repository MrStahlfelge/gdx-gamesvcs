package de.golfgl.gdxgamesvcs;

import com.badlogic.gdx.Gdx;

/**
 * GLThread safe IGameServiceListener wrapper.
 * 
 * Ensure the wrapped listener is called on GLThread.
 * 
 * @author mgsx
 * 
 * TODO pull-up this class to core package, could be usefull for any implementations. or
 * better, force implementation to call listener in GLThread (postRunnable)
 *
 */
public class GameServiceSafeListener implements IGameServiceListener {

	private IGameServiceListener listener;
	
	/**
	 * Create the wrapper
	 * @param listener the wrapped listener
	 */
	public GameServiceSafeListener(IGameServiceListener listener) {
		super();
		this.listener = listener;
	}

	@Override
	public void gsConnected() {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.gsConnected();
			}
		});
	}

	@Override
	public void gsDisconnected() {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.gsDisconnected();
			}
		});
	}

	@Override
	public void gsErrorMsg(final GsErrorType et, final String msg) {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.gsErrorMsg(et, msg);
			}
		});
	}

	@Override
	public void gsGameStateLoaded(final byte[] gameState) {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.gsGameStateLoaded(gameState);
			}
		});
	}

}
