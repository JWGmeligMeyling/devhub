package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.persistence.EntityNotFoundException;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.git.models.CreateRepositoryModel;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

@Slf4j
public class GitBackendImpl implements GitBackend {
	
	private final File root;
	private final Groups groups;
	
	@Inject
	public GitBackendImpl(@Named("directory.mirrors") final File root, final Groups groups) {
		this.root = root;
		this.groups = groups;
	}

	@Override
	public RepositoyProxy open(User user, String repoName) throws ServiceNotAuthorizedException, RepositoryNotFoundException {

		final Path rootPath = root.toPath();
		final Path newPath = rootPath.resolve(repoName);
		
		Group group;
		
		try {
			group = groups.findByRepoName(repoName);
		} catch (EntityNotFoundException e) {
			log.debug("Group could not be found for repository {}", repoName);
			throw new RepositoryNotFoundException(repoName, e);
		}
		
		if(!group.getMembers().contains(user)) {
			log.debug("User {} is not a member of group {}", user, group);
			throw new ServiceNotAuthorizedException();
		}
		
		File folder = newPath.toFile();
		
		try {
			Git git = Git.open(folder);
			return new RepositoryProxyImpl(git, repoName);
		}
		catch (IOException e) {
			throw new RepositoryNotFoundException(folder, e);
		}
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
