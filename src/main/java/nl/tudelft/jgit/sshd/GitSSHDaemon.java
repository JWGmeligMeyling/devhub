package nl.tudelft.jgit.sshd;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Slf4j
@Singleton
public class GitSSHDaemon {

	private final SshServer sshd;
	
	@Inject
	public GitSSHDaemon(
			@Named("jgit.sshd.host") final String host,
			@Named("jgit.sshd.port") final int port,
			final KeyPairProvider keyPairProvider,
			final PublickeyAuthenticator publickeyAuthenticator,
			final PasswordAuthenticator passwordAuthenticator,
			final GitCommandFactory commandFactory) {
		
		sshd = SshServer.setUpDefaultServer();
		addProperty(SshServer.IDLE_TIMEOUT, "10000");
		
		sshd.setHost(host);
		sshd.setPort(port);
		sshd.setKeyPairProvider(keyPairProvider);
		sshd.setPublickeyAuthenticator(publickeyAuthenticator);
		sshd.setPasswordAuthenticator(passwordAuthenticator);
		sshd.setCommandFactory(commandFactory);
		sshd.setShellFactory(new NoShell());
		
		try {
			start();
		}
		catch (IOException e) {
			log.error("Failed to start SSH deamon", e);
		}
	}
	
	private static class NoShell implements Factory<Command> {

		@Override
		public Command create() {
			return new AbstractCommand() {

				@Override
				protected int execute() throws IOException {
					out.write("**** Welcome to the Devhub ****\n".getBytes());
					out.flush();
					return 1;
				}
				
			};
		}

	}
	
	/**
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 * @see org.apache.sshd.SshServer#getProperties()
	 */
	public void addProperty(final String key, final String value) {
		sshd.getProperties().put(key, value);
	}

	/**
	 * @throws IOException
	 * @see org.apache.sshd.SshServer#start()
	 */
	public void start() throws IOException {
		sshd.start();
		log.info("SSH Daemon started at {}:{}", sshd.getHost(), sshd.getPort());
	}

	/**
	 * @throws InterruptedException
	 * @see org.apache.sshd.SshServer#stop()
	 */
	public void stop() throws InterruptedException {
		sshd.stop();
	}

}
