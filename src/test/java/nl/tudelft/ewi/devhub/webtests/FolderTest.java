package nl.tudelft.ewi.devhub.webtests;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.Test;

import nl.tudelft.ewi.devhub.webtests.utils.WebTest;
import nl.tudelft.ewi.devhub.webtests.views.FolderView;
import nl.tudelft.ewi.devhub.webtests.views.TextFileView;
import nl.tudelft.ewi.git.models.EntryType;
import nl.tudelft.ewi.jgit.proxy.CommitProxy;
import nl.tudelft.ewi.jgit.proxy.GitException;

public class FolderTest extends WebTest {
	
	private static final String REPOSITORY_PATH = "courses/ti1705/group-1";
	private static final String REPO_NAME = "group-1/";
	
	private FolderView getFolderView() throws RepositoryNotFoundException, GitException {
		FolderView view = openLoginScreen()
				.login(NET_ID, PASSWORD)
				.toProjectsView()
				.listMyProjects()
				.get(0).click()
				.listCommits()
				.get(0).click()
				.viewFiles();
		
		CommitProxy commit = gitBackend.open(REPOSITORY_PATH).unsafe()
				.getBranch("master")
				.getCommits(0, 10).get(0);
		
		assertEquals(commit.getAuthor(), view.getAuthorHeader());
		assertEquals(commit.getMessage(), view.getMessageHeader());
		
		return view;
	}
	
	/**
	 * <h1>Opening a folder in the repository .</h1>
	 * 
	 * Given that:
	 * <ol>
	 *   <li>I am successfully logged in.</li>
	 *   <li>I have a project.</li>
	 *   <li>There is a commit in the project.</li>
	 *   <li>I list the folders in the repository at the commit.</li>
	 * </ol>
	 * When:
	 * <ol>
	 *   <li>I click on List directory.</li>
	 * </ol>
	 * Then:
	 * <ol>
	 *   <li>I am redirected to the folder page.</li>
	 *   <li>The elements in the folder page match the elements in the repository.</li>
	 * </ol>
	 * @throws IOException 
	 * @throws GitException 
	 */
	@Test
	public void testFileExplorer() throws InterruptedException, GitException, IOException {
		FolderView view = getFolderView();
		assertThat(view.getPath(), containsString(REPO_NAME));
		
		Map<String, EntryType> expected = gitBackend.open(REPOSITORY_PATH)
				.unsafe()
				.getBranch("master")
				.getCommits(0, 10).get(0).showTree("");
		Map<String, EntryType> actual = view.getDirectoryEntries();
		assertEquals(expected, actual);
	}

	/**
	 * <h1>Opening a text file in the repository at a certain commit.</h1>
	 * 
	 * Given that:
	 * <ol>
	 *   <li>I am successfully logged in.</li>
	 *   <li>I have a project.</li>
	 *   <li>There is a commit in the project.</li>
	 * </ol>
	 * When:
	 * <ol>
	 *   <li>I click a file in the folder view.</li>
	 * </ol>
	 * Then:
	 * <ol>
	 *   <li>I am redirected to the file page.</li>
	 *   <li>The contents in the file match the contents in the repository.</li>
	 * </ol>
	 * @throws GitException 
	 * @throws IOException 
	 */	
	@Test
	public void testOpenFile() throws GitException, IOException {
		TextFileView view = getFolderView()
				.getDirectoryElements()
				.stream()
				.filter(a -> a.getType().equals(EntryType.TEXT))
				.findFirst()
				.get().click();
		
		CommitProxy commit =  gitBackend.open(REPOSITORY_PATH)
				.unsafe()
				.getBranch("master")
				.getCommits(0, 10).get(0);
		
		String fileName = view.getFilename();
		assertThat(view.getPath(), containsString(REPO_NAME));
		assertEquals(commit.getAuthor(), view.getAuthorHeader());
		assertEquals(commit.getMessage(), view.getMessageHeader());
		
		InputStream expected = commit.showFile(fileName).openStream();
		InputStream actual = IOUtils.toInputStream(view.getContent());
		IOUtils.contentEquals(expected, actual);
	}
	
}
