package nl.tudelft.ewi.devhub.server;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import java.io.File;
import java.lang.annotation.Annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.backend.AuthenticationBackend;
import nl.tudelft.ewi.devhub.server.backend.MailBackend;
import nl.tudelft.ewi.devhub.server.database.DbModule;
import nl.tudelft.ewi.devhub.server.web.templating.TranslatorFactory;
import nl.tudelft.ewi.jgit.proxy.GitBackend;
import nl.tudelft.ewi.jgit.proxy.GitBackendImpl;
import nl.tudelft.ewi.jgit.proxy.GitServletModule;

import org.jboss.resteasy.plugins.guice.ext.JaxrsModule;
import org.reflections.Reflections;

@Slf4j
public class DevhubModule extends ServletModule {
	
	private final File rootFolder;
	private final Config config;

	public DevhubModule(Config config, File rootFolder) {
		this.config = config;
		this.rootFolder = rootFolder;
	}

	@Override
	protected void configureServlets() {
		install(new DbModule());
		install(new JaxrsModule());
		requireBinding(ObjectMapper.class);
		
		bind(Config.class).toInstance(config);
		
		bind(File.class).annotatedWith(Names.named("directory.templates")).toInstance(new File(rootFolder, "templates"));
		bind(File.class).annotatedWith(Names.named("directory.mirrors")).toInstance(config.getMirrorsDir());
		bind(File.class).annotatedWith(Names.named("jgit.sshd.certDir")).toInstance(config.getSSHCertDir());
		
		bind(Integer.class).annotatedWith(Names.named("jgit.sshd.port")).toInstance(config.getSSHPort());
		bind(String.class).annotatedWith(Names.named("jgit.sshd.host")).toInstance(config.getSSHHost());
		bind(MailBackend.class).toInstance(new MailBackend(config){
			
			@Override
			public void sendMail(Mail mail) {
				log.info("Caught mail : {}", mail);
			}
			
		});
		
		bind(TranslatorFactory.class).toInstance(new TranslatorFactory("i18n.devhub"));
		bind(GitBackend.class).to(GitBackendImpl.class);
		bind(AuthenticationBackend.class).toInstance(new AuthenticationBackend() {
			
			@Override
			public boolean authenticate(String netId, String password) {
				return netId.equals("jgmeligmeyling") &&
					password.equals("a9QrW32a!");
			}
		});
		
//		TODO Unused binding
//		bind(LdapUserProcessor.class).to(PersistingLdapUserProcessor.class);
		
		install(new GitServletModule());
	      
		findResourcesWith(Path.class);
		findResourcesWith(Provider.class);
	}
	
	private void findResourcesWith(Class<? extends Annotation> ann) {
		Reflections reflections = new Reflections(getClass().getPackage().getName());
		for (Class<?> clasz : reflections.getTypesAnnotatedWith(ann)) {
			log.info("Registering resource {}", clasz);
			bind(clasz);
		}
	}

}
