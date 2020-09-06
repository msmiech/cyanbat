package at.grueneis.game.framework;

public interface Audio
{
	Music newMusic(String filename);
	
	Sound newSound(String filename);
}
