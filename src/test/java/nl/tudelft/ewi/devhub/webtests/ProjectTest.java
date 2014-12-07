package nl.tudelft.ewi.devhub.webtests;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import nl.tudelft.ewi.devhub.server.util.DiffLine;
import nl.tudelft.ewi.devhub.webtests.utils.WebTest;
import nl.tudelft.ewi.devhub.webtests.views.DiffView;
import nl.tudelft.ewi.devhub.webtests.views.DiffView.DiffElement;
import nl.tudelft.ewi.devhub.webtests.views.ProjectView;
import nl.tudelft.ewi.devhub.webtests.views.ProjectView.Commit;
import nl.tudelft.ewi.jgit.proxy.BranchProxy;
import nl.tudelft.ewi.jgit.proxy.CommitProxy;
import nl.tudelft.ewi.jgit.proxy.Diff;
import nl.tudelft.ewi.jgit.proxy.GitException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.Test;

public class ProjectTest extends WebTest {
	
	private static final String REPOSITORY_PATH = "courses/ti1705/group-1";
	
	/**
	 * <h1>Opening a project overview .</h1>
	 * 
	 * Given that:
	 * <ol>
	 *   <li>I am successfully logged in.</li>
	 *   <li>I have a project.</li>
	 *   <li>There is a commit in the project.</li>
	 * </ol>
	 * When:
	 * <ol>
	 *   <li>I click on a project in the project list.</li>
	 * </ol>
	 * Then:
	 * <ol>
	 *   <li>I am redirected to the project page.</li>
	 * </ol>
	 * @throws GitException 
	 * @throws RepositoryNotFoundException 
	 */
	@Test
	public void testListCommits() throws RepositoryNotFoundException, GitException {
		ProjectView view = openLoginScreen()
				.login(NET_ID, PASSWORD)
				.toProjectsView()
				.listMyProjects()
				.get(0).click();
		
		List<Commit> commits = view.listCommits();
		List<CommitProxy> expected = gitBackend
				.open(REPOSITORY_PATH).unsafe()
				.getBranch("master").getCommits(0, 25);
		assertEquals(expected.size(), commits.size());
		
		for(int i = 0, s = expected.size(); i < s; i++) {
			Commit commit = commits.get(i);
			CommitProxy model = expected.get(i);
			assertEquals(commit.getAuthor(), model.getAuthor());
			assertEquals(commit.getMessage(), model.getMessage());
		}		
	}
	
	/**
	 * <h1>Opening a project overview .</h1>
	 * 
	 * Given that:
	 * <ol>
	 *   <li>I am successfully logged in.</li>
	 *   <li>I have a project.</li>
	 *   <li>There is a commit in the project.</li>
	 * </ol>
	 * When:
	 * <ol>
	 *   <li>I click on a project in the project list.</li>
	 * </ol>
	 * Then:
	 * <ol>
	 *   <li>I am redirected to the diff page.</li>
	 * </ol>
	 * @throws IOException 
	 * @throws GitException 
	 */
	@Test
	public void testViewCommitDiff() throws GitException, IOException {
		ProjectView projectView = openLoginScreen()
				.login(NET_ID, PASSWORD)
				.toProjectsView()
				.listMyProjects().get(0).click();
		
		int amountOfCommits = projectView.listCommits().size();
		
		BranchProxy branchProxy = gitBackend
				.open(REPOSITORY_PATH).unsafe()
				.getBranch("master");
		
		for(int i = 0; i < amountOfCommits; i++) {
			DiffView view = projectView
				.listCommits()
				.get(i).click();
			
			CommitProxy commit = branchProxy.getCommits(i, 1).get(0);
			assertDiffViewInvariant(view, commit);
			projectView = view.navigateBack((driver) -> 
				new ProjectView(driver));
		}
	}
	
	private static void assertDiffViewInvariant(final DiffView view, final CommitProxy commit) throws GitException, IOException {
		assertEquals(commit.getAuthor(), view.getAuthorHeader());
		assertEquals(commit.getMessage(), view.getMessageHeader());
		
		List<DiffElement> elements = view.listDiffs();
		List<Diff> diffs = commit.getDiff();
		
		for(int j = 0, s = elements.size(); j < s; j++) {
			Diff diff = diffs.get(j);
			DiffElement element = elements.get(j);
			assertDiffEquals(diff, element);
		}
	}
	
	private static void assertDiffEquals(final Diff expected, final DiffElement actual) {
		assertEquals(expected.getType(), actual.getType());
//		TODO New/old path are unconsistent between web and git as they only together on renames
//		assertEquals(expected.getNewPath(), actual.getNewPath());
//		assertEquals(expected.getOldPath(), actual.getOldPath());
		
		List<DiffLine> expectedlines = expected.getLines();
		List<DiffLine> actualLines = actual.getLines();
		
		for(int j = 0, t = expectedlines.size(); j < t; j++) {
			DiffLine expectedLine = expectedlines.get(j);
			DiffLine actualLine = actualLines.get(j);
			assertEquals(expectedLine.getNewLineNumber(), actualLine.getNewLineNumber());
			assertEquals(expectedLine.getOldLineNumber(), actualLine.getOldLineNumber());
			assertEquals(expectedLine.getModifier(), actualLine.getModifier());
			assertEquals(expectedLine.getContents().replaceAll("[ \t]+", ""),
					actualLine.getContents().replaceAll("[ \t]+", ""));
		}
	}
	
}
