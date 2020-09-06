package at.grueneis.game.framework;

import android.content.Context;

public interface Game
{
	Input getInput();
	FileIO getFileIO();
	Graphics getGraphics();
	Audio getAudio();
	void setScreen(Screen screen);
	Screen getCurrentScreen();
	Screen getStartScreen();
	Context getContext();
}
