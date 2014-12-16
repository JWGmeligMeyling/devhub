package nl.tudelft.ewi.devhub.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class RepositoryUrlResolver {

	private final Config config;
	
	@Inject
	public RepositoryUrlResolver(final Config config) {
		this.config = config;
	}
	
	public String resolveSsh(final String username, final String path) {
		return String.format("ssh://%s@%s:%d/%s", username,
				config.getSSHHost(), config.getSSHPort(), path);
	}
	
	public String resolveHttp(String path) {
		return String.format("%s:%d/remote/%s", config.getHttpUrl(),
				config.getHttpPort(), path);
	}
	
}
