package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javax.persistence.EntityNotFoundException;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Slf4j
@Singleton
public class GitBackendImpl implements GitBackend {
	
	private final File root;
	private final Path rootPath;
	private final Provider<Groups> groupsProvider;
	
	@Inject
	public GitBackendImpl(@Named("directory.mirrors") final File root,
			final Provider<Groups> groupsProvider) {
		this.root = root;
		this.rootPath = root.toPath();
		this.groupsProvider = groupsProvider;
		log.debug("Initialized GitBackend in {}", root);
	}
	
	@Override
	public void create(final String path, final String templateRepository) throws RepositoryExists, GitException {
		Preconditions.checkNotNull(path);
		Preconditions.checkArgument(!path.isEmpty());
		
		File folder = new File(root, path);
		
		if(folder.exists()) throw new RepositoryExists(path);
		// TODO Check if parent folders between current folder and root do not contain git repos
		
		try {
			if(templateRepository != null) {
				log.info("Cloning {} into {}", templateRepository, folder);
				Git.cloneRepository().setDirectory(folder).setURI(templateRepository).call();
				log.info("Finished clone in {}", folder);
			}
			else {
				Git.init().setDirectory(folder).call();
				log.info("Initialized new repository in {}", folder);
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

	@Override
	public RepositoryProxyPromise open(final String repoName) {
		return new RepositoryProxyPromiseImpl(repoName);
	}

	class RepositoryProxyPromiseImpl implements RepositoryProxyPromise {
		
		private final String repoName;
		private final Path path;
		
		public RepositoryProxyPromiseImpl(final String repoName) {
			this.repoName = repoName;
			this.path = rootPath.resolve(repoName);
		}

		@Override
		public RepositoryProxy as(User user)
				throws RepositoryNotFoundException,
				ServiceNotAuthorizedException {
			
			Group group;
			
			try {
				group = groupsProvider.get().findByRepoName(repoName);
			} catch (EntityNotFoundException e) {
				log.debug("Group could not be found for repository {}", repoName);
				throw new RepositoryNotFoundException(repoName, e);
			}
			
			if(!group.getMembers().contains(user)) {
				log.debug("User {} is not a member of group {}", user, group);
				throw new ServiceNotAuthorizedException();
			}
			
			return unsafe();
		}

		@Override
		public RepositoryProxy unsafe() throws RepositoryNotFoundException {
			File folder = path.toFile();
			
			try {
				Git git = Git.open(folder);
				return new RepositoryProxy(git, repoName);
			}
			catch (IOException e) {
				throw new RepositoryNotFoundException(folder, e);
			}
		}
		
	}
	
}
