package at.grueneis.game.framework.code;

import java.io.IOException;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;
import at.grueneis.game.framework.Audio;
import at.grueneis.game.framework.Music;
import at.grueneis.game.framework.Sound;

public class AndroidAudio implements Audio
{
	AssetManager assets;
	SoundPool soundPool;
	
	public AndroidAudio(Activity activity)
	{
		activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.assets = activity.getAssets();
		soundPool = new SoundPool(20, AudioManager.STREAM_MUSIC, 0);
	}
	
	public Music newMusic(String filename)
	{
		try
		{
			AssetFileDescriptor afd = assets.openFd(filename);
			return new AndroidMusic(afd);
		}
		catch (IOException exc)
		{
			throw new RuntimeException("Music-file <" + filename + "> not found!");
		}
	}
	
	public Sound newSound(String filename)
	{
		try
		{
			AssetFileDescriptor afd = assets.openFd(filename);
			int soundID = soundPool.load(afd, 0);
			return new AndroidSound(soundID, soundPool);
		}
		catch (IOException exc)
		{
			throw new RuntimeException("Sound-file <" + filename + "> not found!");
		}
	}
	
}
