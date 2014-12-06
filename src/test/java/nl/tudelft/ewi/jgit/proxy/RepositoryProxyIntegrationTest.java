package nl.tudelft.ewi.jgit.proxy;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;

import nl.tudelft.ewi.git.models.CommitModel;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class RepositoryProxyIntegrationTest {
	
	private static File repoFolder;
	private static Git git;
	private static RepositoryProxyImpl proxy;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		repoFolder = Files.createTempDir();
		git = Git.init().setDirectory(repoFolder).call();
		proxy = new RepositoryProxyImpl(git, repoFolder.getPath());

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
		
		CommitModel expected = new CommitModel();
		expected.setAuthor("Jan-Willem Gmelig Meyling", "j.gmeligmeyling@student.tudelft.nl");
		expected.setCommit(commit.getId().getName());
		expected.setMessage(commit.getShortMessage());
		expected.setParents(new String[0]);
		expected.setTime(commit.getCommitTime());
		
		assertEquals(
			Lists.newArrayList(expected),
			proxy.getBranch("master").getCommits(0, 20)
		);
		
		proxy
			.getCommit(commit.getId().getName())
			.getDiff();
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		repoFolder.delete();
	}

}
