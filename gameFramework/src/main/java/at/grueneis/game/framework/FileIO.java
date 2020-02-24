package at.grueneis.game.framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileIO
{
	InputStream readAsset(String filename) throws IOException;
	
	InputStream readFile(String filename) throws IOException;
	
	OutputStream writeFile(String filename) throws IOException;
}
