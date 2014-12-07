package nl.tudelft.jgit.sshd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;

@Slf4j
public abstract class AbstractCommand implements Command, Runnable {

	protected boolean started = false;
	protected ServerSession session;
	protected InputStream in;
	protected OutputStream out;
	protected OutputStream err;
	protected ExitCallback callback;
	
	public void setInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream(OutputStream err) {
		this.err = err;
	}

	@Override
	public void setExitCallback(ExitCallback callback) {
		this.callback = callback;
	}
	
	@Override
	public final void run() {
		if(!started)
			throw new IllegalStateException("GitCommand has not started yet!");
		
		int result = 0;
		
		try {
			result = execute();
		}
		catch (Throwable e) {
			result = 1;
			
			try {
				out.flush();
			}
			catch (Throwable e1) {
				log.debug(e1.getMessage(), e);
			}
			
			try {
				err.flush();
			}
			catch (Throwable e1) {
				log.debug(e1.getMessage(), e);
			}

			log.debug(e.getMessage(), e);
		}
		finally {
			if(callback != null)
				callback.onExit(result);
		}
	}
	
	protected abstract int execute() throws IOException;

	@Override
	public void start(Environment env) {
		if(started)
			throw new IllegalStateException("GitCommand has already started");
		
		if (in == null || out == null || err == null)
			throw new IllegalStateException(new NullPointerException(
					"Streams should be set on the GitCommand"));
		
		started = true;
		run();
	}
	
	@Override
	public void destroy() {
	}

}
