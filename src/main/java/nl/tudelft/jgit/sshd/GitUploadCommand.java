package nl.tudelft.jgit.sshd;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

public class GitUploadCommand extends GitCommand {

	private final UploadPackFactory<GitCommand> uploadPackFactory;
	
	public GitUploadCommand(
			final String command,
			final ExecutorService executorService,
			final RepositoryResolver<GitCommand> repositoryResolver,
			final UploadPackFactory<GitCommand> uploadPackFactory) {
		
		super(command, executorService, repositoryResolver);
		this.uploadPackFactory = uploadPackFactory;
	}

	@Override
	protected String getCommandName() {
		return "git-upload-pack";
	}

	@Override
	protected int execute(Repository repository) throws IOException {
		try {
			final UploadPack up = uploadPackFactory.create(this, repository);
			up.upload(in, out, err);
			return 0;
		}
		catch (ServiceNotEnabledException | ServiceNotAuthorizedException e) {
			err.write(e.getMessage().getBytes());
			err.flush();
			return 1;
		}
	}

}
