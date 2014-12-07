package nl.tudelft.ewi.jgit.proxy;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;

public class RepositoryProxyIntegrationTest {
	
	private static File repoFolder;
	private static Git git;
	private static RepositoryProxy proxy;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		repoFolder = Files.createTempDir();
		git = Git.init().setDirectory(repoFolder).call();
		proxy = new RepositoryProxy(git, repoFolder.getPath());

		assertTrue("A bare repository has no branch except for the HEAD", proxy.getBranches().isEmpty());
		assertTrue("A bare repository has no branch except for the HEAD", proxy.getTags().isEmpty());
	}

	@Test(expected=GitException.class)
	public void testRetrieveNonExistingCommit() throws GitException {
		proxy.getCommit("166d5ff142e00e22ae521fdbf31664420b455beb");
	}
	
	@Test
	public void testRetrieveExistingCommit() throws Exception {
		try(FileWriter writer = new FileWriter(new File(repoFolder, "README.md"))) {
			writer.write("Some content\n");
			git.add().addFilepattern("README.md").call();
		}

		RevCommit commit = git.commit()
			.setMessage("Initial commit")
			.call();
		
		assertThat(proxy.getBranches(), Matchers.hasSize(1));
		
		List<CommitProxy> commits = proxy.getBranch("master").getCommits(0, 20);
		assertThat(commits, Matchers.hasSize(1));
		
		CommitProxy actual = commits.iterator().next();
		assertEquals("Initial commit", actual.getMessage());
		assertEquals(commit.getId().getName(), actual.getCommit());
		
		proxy
			.getCommit(commit.getId().getName())
			.getDiff();
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		repoFolder.delete();
	}

}
