package nl.tudelft.jgit.sshd;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

public abstract class GitCommand extends AbstractCommand implements SessionAware {
	
	protected final String command;
	private final String repoName;
	private final ExecutorService executorService;
	private final RepositoryResolver<GitCommand> repositoryResolver;
	
	protected GitCommand(
			final String command,
			final ExecutorService executorService,
			final RepositoryResolver<GitCommand> repositoryResolver) {
		
		this.command = command;
		this.executorService = executorService;
		this.repositoryResolver = repositoryResolver;
		this.repoName = buildRepositoryName(command);
	}
	
	private String buildRepositoryName( String command ) {
        int start = getCommandName().length() + 2;
        final String temp = command.substring( start );
        return temp.substring(1, temp.indexOf( "'" )).replace( '\\', '/' );
    }
	
    protected abstract String getCommandName();
    
	protected final int execute() throws IOException {
		int result = 0;
		
		if (session == null)
			throw new IllegalStateException(new NullPointerException(
			"Session should not be null"));
		
		try {
			Repository repo = repositoryResolver.open(this, repoName);
			result = execute(repo);
		}
		catch (RepositoryNotFoundException | ServiceMayNotContinueException
				| ServiceNotAuthorizedException | ServiceNotEnabledException e) {
			
			result = 1;
			err.write(e.getMessage().getBytes());
			err.write('\n');
			err.flush();
		}
		
		return result;
	}
	
	protected abstract int execute(Repository repository) throws IOException;

	@Override
	public void setSession(ServerSession session) {
		this.session = session;
	}

	@Override
	public void start(Environment env) {
		if(started)
			throw new IllegalStateException("GitCommand has already started");
		
		if (in == null || out == null || err == null)
			throw new IllegalStateException(new NullPointerException(
					"Streams should be set on the GitCommand"));
		
		executorService.execute(this);
		started = true;
	}
	
	public <T> T getSessionAttribute(AttributeKey<T> key) {
		if (session == null)
			throw new IllegalStateException(new NullPointerException(
					"Session should not be null"));
		return session.getAttribute(key);
	}
	
}
