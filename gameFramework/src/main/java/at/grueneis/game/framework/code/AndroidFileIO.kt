package at.grueneis.game.framework.code;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.res.AssetManager;
import android.os.Environment;
import at.grueneis.game.framework.FileIO;

public class AndroidFileIO implements FileIO
{
	AssetManager assets;
	String sdPath;
	
	public AndroidFileIO(AssetManager assets)
	{
		this.assets = assets;
		sdPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
	}
	
	public InputStream readAsset(String filename) throws IOException
	{
		return assets.open(filename);
	}
	
	public InputStream readFile(String filename) throws IOException
	{
		return new FileInputStream(sdPath + filename);
	}
	
	public OutputStream writeFile(String filename) throws IOException
	{
		return new FileOutputStream(sdPath + filename);
	}
	
}
