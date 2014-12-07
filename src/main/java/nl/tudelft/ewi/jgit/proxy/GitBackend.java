package nl.tudelft.ewi.jgit.proxy;

import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.git.models.CreateRepositoryModel;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.inject.ImplementedBy;

@ImplementedBy(GitBackendImpl.class)
public interface GitBackend {

	RepositoyProxy open(User user, String path) throws RepositoryNotFoundException,
			ServiceNotAuthorizedException;
	
	void create(CreateRepositoryModel repoModel) throws RepositoryExists, GitException;
	
	public class RepositoryExists extends Exception {
		
		private static final long serialVersionUID = -7937371281697239663L;
		
		public RepositoryExists(String path) {
			super(String.format("Repository exists: %s", path));
		}
		
		public RepositoryExists(String path, Throwable cause) {
			super(String.format("Repository exists: %s", path), cause);
		}
		
	}
	
}
