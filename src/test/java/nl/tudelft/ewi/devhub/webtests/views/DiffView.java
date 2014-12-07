package nl.tudelft.ewi.devhub.webtests.views;

import static org.junit.Assert.*;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import nl.tudelft.ewi.devhub.server.util.DiffLine;
import nl.tudelft.ewi.jgit.proxy.Diff;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class DiffView extends View {
	
	private static final By HEADERS = By.xpath("//span[@class='headers']");
	private static final By DROPDOWN_CARET = By.xpath("//button[contains(@class,'dropdown-toggle')]");
	private static final By MESSAGE_HEADER = By.xpath(".//h2[@class='header']");
	private static final By AUTHOR_SUB_HEADER = By.xpath(".//h5[@class='subheader']");
	private static final By VIEW_FILES_BUTTON = By.xpath("//a[starts-with(normalize-space(.), 'View files')]");

	private final WebElement headers;
	
	public DiffView(WebDriver driver) {
		super(driver);
		this.headers = getDriver().findElement(HEADERS);
		assertInvariant();
	}

	private void assertInvariant() {
		assertTrue(currentPathStartsWith("/projects"));
		assertNotNull(headers);
		assertNotNull(getDriver().findElement(DROPDOWN_CARET));
	}
	
	public FolderView viewFiles() {
		assertInvariant();
		
		WebElement container = getDriver().findElement(By.className("container"));
		WebElement dropdownCaret = container.findElement(DROPDOWN_CARET);
		dropdownCaret.click();
		WebElement viewFilesButton = container.findElement(VIEW_FILES_BUTTON);
		viewFilesButton.click();
		
		return new FolderView(getDriver());
	}
	
	/**
	 * @return the {@link DiffElement DiffElements} in this {@code DiffView}
	 */
	public List<DiffElement> listDiffs() {
		assertInvariant();
		WebElement container = getDriver().findElement(By.className("container"));
		return listDiffs(container);
	}
	
	private List<DiffElement> listDiffs(WebElement container) {
		List<WebElement> elements = container.findElements(By.xpath("//div[@class='diff box']"));
		
		return Lists.transform(elements, new Function<WebElement, DiffElement>() {

			@Override
			public DiffElement apply(WebElement element) {
				return DiffElement.build(element);
			}
			
		});
	}
	
	/**
	 * @return the text content of the author header
	 */
	public String getAuthorHeader() {
		return headers.findElement(AUTHOR_SUB_HEADER).getText();
	}
	
	/**
	 * @return the text content of the message header
	 */
	public String getMessageHeader() {
		return headers.findElement(MESSAGE_HEADER).getText();
	}
	
	@Data
	@ToString(callSuper=true)
	@EqualsAndHashCode(callSuper=true)
	public static class DiffElement extends Diff {
		
		private final WebElement element;
		
		/**
		 * Build a {@link DiffElement} from a {@link WebElement} in the {@link DiffView}
		 * @param element the {@link WebElement} to be converted into a {@link DiffElement}
		 * @return the created {@link DiffElement}
		 */
		public static DiffElement build(WebElement element) {
			DiffElement result = new DiffElement(element);
			
			WebElement header = element.findElement(By.tagName("h5"));
			String typeStr = header.findElement(By.tagName("span")).getText();
			Type type = getTypeFor(typeStr);
			result.setType(type);
			
			String headerText = header.getText();
			headerText = headerText.substring(headerText.indexOf(" ") + 1);
			
			switch (type) {
				case ADD:
				case MODIFY:
					result.setNewPath(headerText);
					break;
				case DELETE:
					result.setOldPath(headerText);
					break;
				default:
					String[] split = headerText.split(" -> ");
					result.setOldPath(split[0]);
					result.setNewPath(split[1]);
					break;
			}
			
			result.setLines(getDiffLinesFor(element.findElements(By.tagName("tr"))));
			return result;
		}
		
		private static List<DiffLine> getDiffLinesFor(List<WebElement> rows) {
			List<DiffLine> diffLines = Lists.newArrayList();
			
			for(WebElement codeRow : rows) {
				List<WebElement> parts = codeRow.findElements(By.tagName("td"));
				assert parts.size() == 3 : "Each diff line should have a column for old and new line number; and line contents.";
				String oldLineNumber = parts.get(0).getText();
				String newLineNumber = parts.get(1).getText();
				char modifier = getModifierFor(parts.get(2));
				String lineContents = parts.get(2).getText();
				diffLines.add(new DiffLine(oldLineNumber, newLineNumber, modifier, lineContents));
			}
			
			return diffLines;
		}
		
		private static char getModifierFor(WebElement column) {
			String styles = column.getAttribute("class");
			
			if(styles.contains("add")) {
				return DiffLine.MODIFIER_ADDED;
			}
			else if (styles.contains("delete")) {
				return DiffLine.MODIFIER_REMOVED;
			}
			else {
				return DiffLine.MODIFIER_UNCHANGED;
			}
		}
		
		private static Type getTypeFor(String value) {
			if(value.equalsIgnoreCase("Created")) {
				return Type.ADD;
			}
			else if (value.equalsIgnoreCase("Copied")) {
				return Type.COPY;
			}
			else if (value.equalsIgnoreCase("Deleted")) {
				return Type.DELETE;
			}
			else if (value.equalsIgnoreCase("Modified")) {
				return Type.MODIFY;
			}
			else if (value.equalsIgnoreCase("Moved")) {
				return Type.RENAME;
			}
			return null;
		}
		
	}
	
}
