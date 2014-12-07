package nl.tudelft.ewi.devhub.webtests.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.FileUtils;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.firefox.FirefoxDriver;

public class DevhubTestWatcher extends TestWatcher {
	
	private final FirefoxDriver driver;
	
	public DevhubTestWatcher(final FirefoxDriver driver) {
		this.driver = driver;
	}
	
	@Override
	protected void failed(Throwable e, Description description) {
		try {
			long now = System.currentTimeMillis();
			createScreenshot(e, description, now);
			writeStacktrace(e, description, now);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	private void createScreenshot(Throwable e, Description description, long now) throws IOException {
		File input = driver.getScreenshotAs(OutputType.FILE);
		String filename = String.format("Screenshot-%s-%s.jpg", now, description.getMethodName());
		File output = new File(filename);
		FileUtils.copyFile(input, output);			
	}
	
	private void writeStacktrace(Throwable e, Description description, long now) throws FileNotFoundException {
		String filename = String.format("Stacktrace-%s-%s.txt", now, description.getMethodName());
		e.printStackTrace(new PrintStream(filename));
	}
	
}
