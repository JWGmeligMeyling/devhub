package nl.tudelft.jgit.sshd;

import java.util.concurrent.ExecutorService;

import org.apache.sshd.server.CommandFactory;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

public class GitCommandFactory implements CommandFactory {
	
	private final ExecutorService executorService;
	private final RepositoryResolver<GitCommand> repositoryResolver;
	private final ReceivePackFactory<GitCommand> receivePackFactory;
	private final UploadPackFactory<GitCommand> uploadPackFactory;
	
	public GitCommandFactory(
			final ExecutorService executorService,   
			final RepositoryResolver<GitCommand> repositoryResolver,
			final ReceivePackFactory<GitCommand> receivePackFactory,
			final UploadPackFactory<GitCommand> uploadPackFactory) {
		
		this.executorService = executorService;
		this.repositoryResolver = repositoryResolver;
		this.receivePackFactory = receivePackFactory;
		this.uploadPackFactory = uploadPackFactory;
	}

	public GitCommand createCommand(final String command) {
		if(command.startsWith("git-upload-pack")) {
			return new GitUploadCommand(command, executorService, repositoryResolver, uploadPackFactory);
		}
		else if (command.startsWith("git-receive-pack")) {
			return new GitReceiveCommand(command, executorService, repositoryResolver, receivePackFactory);
		}
		else {
			return new UnknownGitCommand(command, executorService, repositoryResolver);
		}
	}

}
