package at.grueneis.game.framework.code;

import java.io.IOException;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import at.grueneis.game.framework.Music;

public class AndroidMusic implements Music, OnCompletionListener
{
	MediaPlayer mediaPlayer;
	boolean isPrepared;
	
	public AndroidMusic(AssetFileDescriptor afd)
	{
		mediaPlayer = new MediaPlayer();
		try
		{
			mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
			mediaPlayer.prepare();
			isPrepared = true;
			mediaPlayer.setOnCompletionListener(this);
		}
		catch (IOException exc)
		{
			throw new RuntimeException("Could not load music!");
		}
	}
	
	public void onCompletion(MediaPlayer arg0)
	{
		synchronized (this)
		{
			isPrepared = false;
		}
	}
	
	public void play()
	{
		if (mediaPlayer.isPlaying()) return;
		try
		{
			synchronized (this)
			{
				if (!isPrepared) mediaPlayer.prepare();
				mediaPlayer.start();
			}
		}
		catch (Exception exc)
		{
			exc.printStackTrace();
		}
	}
	
	public void stop()
	{
		mediaPlayer.stop();
		synchronized (this)
		{
			isPrepared = false;
		}
	}
	
	public void pause()
	{
		mediaPlayer.pause();
	}
	
	public void setLooping(boolean looping)
	{
		mediaPlayer.setLooping(looping);
	}
	
	public void setVolume(float volume)
	{
		mediaPlayer.setVolume(volume, volume);
	}
	
	public boolean isPlaying()
	{
		return mediaPlayer.isPlaying();
	}
	
	public boolean isStopped()
	{
		return !isPrepared;
	}
	
	public boolean isLooping()
	{
		return mediaPlayer.isLooping();
	}
	
	public void dispose()
	{
		if (mediaPlayer.isPlaying()) mediaPlayer.stop();
		mediaPlayer.release();
	}
	
}
