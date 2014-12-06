package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.git.models.CreateRepositoryModel;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

@Slf4j
public class GitBackendImpl implements GitBackend {
	
	private final File root;
	
	@Inject
	public GitBackendImpl(@Named("directory.mirrors") final File root) {
		this.root = root;
	}

	@Override
	public RepositoyProxy open(String path) throws ServiceNotAuthorizedException, IOException {

		final Path rootPath = root.toPath();
		final Path newPath = rootPath.resolve(path);
		File folder = newPath.toFile();
		Git git = Git.open(folder);
		
		return new RepositoryProxyImpl(git, path);
	}

	@Override
	public void create(final CreateRepositoryModel model) throws RepositoryExists, GitException {
		Preconditions.checkNotNull(model);
		String path = model.getName();
		Preconditions.checkArgument(!path.isEmpty());
		
		final Path rootPath = root.toPath();
		final Path newPath = rootPath.resolve(path);
		File folder = newPath.toFile();
		
		if(folder.exists()) throw new RepositoryExists(path);
		
		for(int i = 0, l = newPath.getNameCount(); i < l; i++) {
			folder =  newPath.subpath(0, i+1).toFile();
			folder.mkdir();
			Preconditions.checkArgument(!new File(folder, ".git").exists(),
				"There shouldn't be a git repository in the path from the mirrors root");
		}
		
		try {
			String templateRepository = model.getTemplateRepository();
			
			if(templateRepository != null) {
				Git.cloneRepository().setDirectory(folder).setURI(templateRepository).call();
			}
			else {
				Git.init().setDirectory(folder).call();
			}
		}
		catch (GitAPIException e) {
			try {
				FileUtils.deleteDirectory(folder);
			} catch (IOException e1) {
				log.warn("Failed to delete repository folder on rollback", e1);
			}
			
			throw new GitException(e);
		}
	}
	
}
