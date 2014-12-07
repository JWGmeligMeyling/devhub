package nl.tudelft.ewi.devhub.server;

import java.io.File;

import nl.tudelft.ewi.build.client.BuildServerBackend;
import nl.tudelft.ewi.build.client.MockedBuildServerBackend;
import nl.tudelft.ewi.devhub.server.backend.AuthenticationBackend;
import nl.tudelft.ewi.devhub.server.backend.Bootstrapper;
import nl.tudelft.ewi.devhub.server.backend.MockedAuthenticationBackend;

import com.google.common.io.Files;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class MockedDevhubServer {

	protected static DevhubServer server;
	protected static File mirrors;
	
	public static void main(String[] args) throws Exception {
		mirrors = Files.createTempDir();
		mirrors.deleteOnExit();

		server = new DevhubServer(new AbstractModule() {
			@Override
			protected void configure() {
				bind(File.class).annotatedWith(Names.named("directory.mirrors")).toInstance(mirrors);
				bind(AuthenticationBackend.class).to(MockedAuthenticationBackend.class);
				bind(BuildServerBackend.class).to(MockedBuildServerBackend.class);
				bind(MockedBuildServerBackend.class).toInstance(new MockedBuildServerBackend(null, null));
			}
		});
		
		server.startServer();
		server.getInstance(Bootstrapper.class).prepare("/simple-environment.json");
	}

}
