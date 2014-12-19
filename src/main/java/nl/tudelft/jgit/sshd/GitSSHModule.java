package nl.tudelft.jgit.sshd;

import java.io.File;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.persistence.EntityNotFoundException;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.backend.AuthenticationBackend;
import nl.tudelft.ewi.devhub.server.database.controllers.SshKeys;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;
import nl.tudelft.ewi.devhub.server.database.entities.SshKey;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.jgit.proxy.BuildHook;
import nl.tudelft.ewi.jgit.proxy.GitBackend;
import nl.tudelft.ewi.jgit.proxy.RepositoryProxy;
import nl.tudelft.ewi.jgit.proxy.BuildHook.BuildHookFactory;

import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.SshException;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;

@Slf4j
public class GitSSHModule extends AbstractModule {
	
	private final AttributeKey<User> USER_KEY = new AttributeKey<User>();
	private final AttributeKey<RepositoryProxy> REPOSITORY = new AttributeKey<RepositoryProxy>();

	@Override
	protected void configure() {
		bind(ExecutorService.class).toInstance(Executors.newCachedThreadPool());
		
		install(new FactoryModuleBuilder()
			.implement(BuildHook.class, BuildHook.class)
			.build(BuildHook.BuildHookFactory.class));
		
		bind(GitSSHDaemon.class).asEagerSingleton();
	}
	
	@Provides
	public GitCommandFactory getGitCommandFactory(
			final ExecutorService executorService,
			final RepositoryResolver<GitCommand> repositoryResolver,
			final ReceivePackFactory<GitCommand> receivePackFactory,
			final UploadPackFactory<GitCommand> uploadPackFactory) {
		
		return new GitCommandFactory(executorService, repositoryResolver,
				receivePackFactory, uploadPackFactory);
	}
	
	@Provides
	public KeyPairProvider getKeyPairProvider(
			@Named("jgit.sshd.certDir") final File certDir) {
		if(!certDir.exists())
			certDir.mkdir();
		File keyFile = new File(certDir, "hostkey.ser");
		return new SimpleGeneratorHostKeyProvider(keyFile.getAbsolutePath());
	}
	
	@Provides
	public PublickeyAuthenticator getPublickeyAuthenticator(
			final Provider<Users> users,
			final Provider<SshKeys> sshKeys) {
		
		return new PublickeyAuthenticator() {
			
			public boolean authenticate(String username, PublicKey key,
					ServerSession session) {
				
				User user;
				
				try {
					user = users.get().findByNetId(username);
				}
				catch (EntityNotFoundException e) {
					log.debug("User {} could not be found", username);
					return false;
				}
				
				List<SshKey> keys = sshKeys.get().getKeysFor(user);
				
				for(SshKey otherKey : keys) {
					try {
						if(otherKey.getPublicKey().equals(key)) {
							session.setAttribute(USER_KEY, user);
							log.debug("{} authenticated using SSH key", username);
							return true;
						}
					}
					catch (SshException e) {
						log.warn(e.getMessage(), e);
					}
				}
				
				log.debug("Failed to authenticate {} using public key", username);
				return false;
			}
		};
	}
	
	@Provides
	public PasswordAuthenticator getPasswordAuthenticator(
			final Provider<AuthenticationBackend> authBackend,
			final Provider<Users> users) {
		
		return new PasswordAuthenticator() {
			
			public boolean authenticate(final String username, final String password,
					ServerSession session) {
				
				User user;
				
				try {
					user = users.get().findByNetId(username);
				}
				catch (EntityNotFoundException e) {
					log.debug("User {} could not be found", username);
					return false;
				}
				
				if(authBackend.get().authenticate(username, password)) {
					session.setAttribute(USER_KEY, user);
					log.debug("{} authenticated using password", username);
					return true;
				}

				log.debug("Failed to authenticate {} using password", username);
				return false;
			}
			
		};
	}
	
	@Provides
	public RepositoryResolver<GitCommand> getRepositoryResolver(
			final Provider<GitBackend> gitBackendProvider) {
		return new RepositoryResolver<GitCommand>() {

			@Override
			public Repository open(GitCommand req, String repoName)
					throws RepositoryNotFoundException,
					ServiceNotAuthorizedException, ServiceNotEnabledException,
					ServiceMayNotContinueException {
				
				log.debug("Looking for repository {}", repoName);
				
				User user = req.getSessionAttribute(USER_KEY);
				RepositoryProxy proxy = gitBackendProvider.get()
						.open(repoName).as(user);
				req.setSessionAttribute(REPOSITORY, proxy);
				return proxy.getRepository();
			}
			
		};
	}
	
	@Provides
	public ReceivePackFactory<GitCommand> getReceivePackFactory(final Provider<BuildHookFactory> buildHookFactory) {
		return new ReceivePackFactory<GitCommand>() {

			@Override
			public ReceivePack create(GitCommand req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				
				User user = req.getSessionAttribute(USER_KEY);
				RepositoryProxy repo = req.getSessionAttribute(REPOSITORY);
				log.debug("User {} requesting upload to {}", user, db);
				
				final ReceivePack rp = new ReceivePack(db);
				rp.setAllowNonFastForwards(user.isAdmin());
				rp.setMaxObjectSizeLimit(50 * 1024 * 1024);
				rp.setPostReceiveHook(buildHookFactory.get().create(repo));
				return rp;
			}
		};
	}
	
	@Provides
	public UploadPackFactory<GitCommand> getUploadPackFactory() {
		return new UploadPackFactory<GitCommand>() {

			@Override
			public UploadPack create(GitCommand req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				
				User user = req.getSessionAttribute(USER_KEY);
				log.debug("User {} requesting download from {}", user, db);
				
				return new UploadPack(db);
			}
		};
	}
	
}
