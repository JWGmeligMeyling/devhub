package nl.tudelft.ewi.devhub.webtests.utils;

import java.io.File;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import nl.tudelft.ewi.build.client.BuildServerBackend;
import nl.tudelft.ewi.build.client.MockedBuildServerBackend;
import nl.tudelft.ewi.devhub.server.DevhubServer;
import nl.tudelft.ewi.devhub.server.backend.AuthenticationBackend;
import nl.tudelft.ewi.devhub.server.backend.Bootstrapper;
import nl.tudelft.ewi.devhub.server.backend.MockedAuthenticationBackend;
import nl.tudelft.ewi.devhub.webtests.views.LoginView;
import nl.tudelft.ewi.jgit.proxy.GitBackend;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public abstract class WebTest {

	public static final String NET_ID = "student1";
	public static final String PASSWORD = "student1";
	
	protected static DevhubServer server;
	protected static GitBackend gitBackend;
	protected static File mirrors;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		mirrors = new File("mirrors/test/");
		mirrors.mkdirs();
		
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
		gitBackend = server.getInstance(GitBackend.class);
	}
	
	private WebDriver driver;
	
	@Before
	public void setUp() {
		this.driver = new FirefoxDriver();
		driver.manage().window().maximize();
	}
	
	public LoginView openLoginScreen() {
		return LoginView.create(driver, "http://localhost:8080");
	}
	
	public WebDriver getDriver() {
		return driver;
	}
	
	@After
	public void tearDown() {
		driver.close();
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		server.stopServer();
	}
	
}