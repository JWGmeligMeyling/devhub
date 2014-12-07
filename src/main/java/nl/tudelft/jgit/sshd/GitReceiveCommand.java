package nl.tudelft.jgit.sshd;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

public class GitReceiveCommand extends GitCommand {

	private final ReceivePackFactory<GitCommand> receivePackFactory;
	
	public GitReceiveCommand(
			final String command,
			final ExecutorService executorService,
			final RepositoryResolver<GitCommand> repositoryResolver,
			final ReceivePackFactory<GitCommand> receivePackFactory) {
		
		super(command, executorService, repositoryResolver);
		this.receivePackFactory = receivePackFactory;
	}

	@Override
	protected String getCommandName() {
		return "git-receive-pack";
	}

	@Override
	protected int execute(Repository repository) throws IOException {
		try {
			final ReceivePack rp = receivePackFactory.create(this, repository);
			rp.receive(in, out, err);
			return 0;
		}
		catch (ServiceNotEnabledException | ServiceNotAuthorizedException e) {
			err.write(e.getMessage().getBytes());
			err.flush();
			return 1;
		}
	}
	
}
