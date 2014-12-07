package nl.tudelft.jgit.sshd;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;

@Slf4j
public class UnknownGitCommand extends GitCommand {
	
	public UnknownGitCommand(String command,
			ExecutorService executorService,
			RepositoryResolver<GitCommand> repositoryResolver) {
		super(command, executorService, repositoryResolver);
	}

	@Override
	protected String getCommandName() {
		return command;
	}

	@Override
	protected int execute(Repository repository) throws IOException {
		 out.write(String.format("Unknown command %s\n", command).getBytes());
		 log.info("Unknown command {}", command);
		 return 1;
	}

}
