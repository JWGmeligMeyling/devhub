package nl.tudelft.ewi.jgit.proxy;

import nl.tudelft.ewi.devhub.server.database.entities.User;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.inject.ImplementedBy;

@ImplementedBy(GitBackendImpl.class)
public interface GitBackend {

	/**
	 * Initialize a new repository.
	 * 
	 * @param path
	 *            Path to the repository to create
	 * @param templateRepository
	 *            Template repository to clone after initialization. When the
	 *            template repository url is empty, an empty repository will be
	 *            initialized.
	 * @throws RepositoryExists
	 *             when a repository already exists at the given path
	 * @throws GitException
	 *             When an exception occurs creating the repository
	 */
	void create(final String path, final String templateRepository) throws RepositoryExists, GitException;

	/**
	 * Open a repository.
	 * 
	 * @param path
	 *            Path to the repository
	 * @return Returns a {@link RepositoryProxyPromise}, to enforce clients to
	 *         do proper authorization before providing access to the
	 *         repository.
	 */
	RepositoryProxyPromise open(String path);
	
	/**
	 * The {@link RepositoryProxyPromise} is used to enforce clients to do
	 * proper authorization before providing access to the repository.
	 * 
	 * @author Jan-Willem Gmelig Meyling
	 */
	interface RepositoryProxyPromise {
		
		/**
		 * Authorize with a {@link User} instance
		 * 
		 * @param user
		 *            {@link User} to authorize with
		 * @return the {@link RepositoryProxy}
		 * @throws RepositoryNotFoundException
		 *             When no such repository exists
		 * @throws ServiceNotAuthorizedException
		 *             When the user is not authorized for this repository
		 */
		RepositoryProxy as(User user)  throws RepositoryNotFoundException,
			ServiceNotAuthorizedException;
		
		/**
		 * Skip authorization for backend access without a session
		 * 
		 * @return the {@link RepositoryProxy}
		 * @throws RepositoryNotFoundException
		 *             When no such repository exists
		 */
		RepositoryProxy unsafe()  throws RepositoryNotFoundException;
	}
	
	/**
	 * Thrown when a repository already exists at the given path
	 * 
	 * @author Jan-Willem Gmelig Meyling
	 *
	 */
	public class RepositoryExists extends Exception {
		
		private static final long serialVersionUID = -7937371281697239663L;
		
		/**
		 * Throw a new {@link RepositoryExists} exception
		 * 
		 * @param path
		 *            The path for the repository
		 */
		public RepositoryExists(String path) {
			super(String.format("Repository exists: %s", path));
		}
		
	}
	
}
