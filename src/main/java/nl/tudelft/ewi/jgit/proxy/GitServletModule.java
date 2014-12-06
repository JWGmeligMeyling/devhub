package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.io.IOException;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.web.filters.BasicAuthFilter;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitFilter;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.servlet.ServletModule;

@Slf4j
public class GitServletModule extends ServletModule {
	
	private final File mirrors;
	
	@Inject
	public GitServletModule(@Named("directory.mirrors") final File mirrors) {
		this.mirrors = mirrors;
	}

	@Override
	protected void configureServlets() {
		filter("/remote/*").through(BasicAuthFilter.class);
		serve("/remote/*").with(GitServlet.class);
	}
	
	@Provides
	public RepositoryResolver<HttpServletRequest> getRepositoryResolver(
			final Provider<Groups> groupsProvider) {
		return new RepositoryResolver<HttpServletRequest>() {
			public Repository open(HttpServletRequest req, String repoName)
					throws RepositoryNotFoundException,
					ServiceNotEnabledException, ServiceNotAuthorizedException {
				
				log.debug("In the repository resolver for {}", repoName);
				
				User user;
				
				try {
					user = (User) req.getAttribute("user");
					Preconditions.checkNotNull(user);
				}
				catch (Exception e) {
					log.debug("User could not be found in request attributes");
					throw new ServiceNotAuthorizedException();
				}
				
				Group group;
				
				try {
					group = groupsProvider.get().findByRepoName(repoName);
				} catch (EntityNotFoundException e) {
					log.debug("Group could not be found for repository {}", repoName);
					throw new RepositoryNotFoundException(repoName, e);
				}
				
				if(!group.getMembers().contains(user)) {
					log.debug("User {} is not a member of group {}", user, group);
					throw new ServiceNotEnabledException();
				}
				
				final File folder = new File(mirrors, repoName);
				
				try {
					return Git.open(folder).getRepository();
				}
				catch (IOException e) {
					log.debug("Repository could not be found in folder {}", folder);
					throw new RepositoryNotFoundException(folder, e);
				}
			}
		};
	}
	
	@Provides
	public ReceivePackFactory<HttpServletRequest> getReceivePackFactory(
			final Provider<Groups> groupsProvider) {
		return new ReceivePackFactory<HttpServletRequest>() {

			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotAuthorizedException {
				
				User user;
				
				try {
					user = (User) req.getAttribute("user");
					Preconditions.checkNotNull(user);
				}
				catch (Exception e) {
					throw new ServiceNotAuthorizedException();
				}
				
				log.debug("User {} requesting upload to {}", user, db);
				
				final ReceivePack rp = new ReceivePack(db);
				rp.setAllowNonFastForwards(user.isAdmin());
				rp.sendMessage("Welcome to Devhub");
				rp.setMaxObjectSizeLimit(50 * 1024 * 1024);
				return rp;
			}
			
		};
	}
	
	@Provides
	public UploadPackFactory<HttpServletRequest> getUploadPackFactory(
			final Provider<Groups> groupsProvider) {
		return new UploadPackFactory<HttpServletRequest>() {

			@Override
			public UploadPack create(HttpServletRequest req, Repository db)
					throws ServiceNotAuthorizedException {
				
				User user;
				
				try {
					user = (User) req.getAttribute("user");
					Preconditions.checkNotNull(user);
				}
				catch (Exception e) {
					throw new ServiceNotAuthorizedException();
				}

				log.debug("User {} requesting download from {}", user, req.getPathInfo());
				return new UploadPack(db);
				
			}
		};
	}
	
	@Singleton
	public static class GitServlet extends MetaServlet {
		
		private static final long serialVersionUID = 7275602190149795296L;

		@Inject
		public GitServlet(
				final GitFilter gitFilter,
				final ReceivePackFactory<HttpServletRequest> receivePackFactory,
				final RepositoryResolver<HttpServletRequest> repositoryResolver,
				final UploadPackFactory<HttpServletRequest> uploadPackFactory) {
			
			super(gitFilter);
			gitFilter.setReceivePackFactory(receivePackFactory);
			gitFilter.setRepositoryResolver(repositoryResolver);
			gitFilter.setUploadPackFactory(uploadPackFactory);
		}
		
	}
	
}
