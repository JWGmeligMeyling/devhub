package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import nl.tudelft.ewi.git.models.DetailedCommitModel;
import nl.tudelft.ewi.git.models.TagModel;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;

public class RepositoryProxy extends AbstractGitProxy {
	
	private final String path;
	
	public RepositoryProxy(final Git git, String path) {
		super(git);
		this.path = path;
	}
	
	public String getName() {
		return path;
	}
	
	public String getPath() {
		return path;
	}
	
	public String getUrl() {
		return "https://localhost/remote/".concat(path);
	}
	
	public List<BranchProxy> getBranches() throws GitException {
		try {
			return git.branchList()
				.call().stream()
				.filter(SKIP_HEAD)
				.map((ref) -> new BranchProxy(git, ref))
				.collect(Collectors.toList());
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
	public BranchProxy getBranch(final String branchName) throws GitException {
		try {
			return git.branchList()
				.call().stream()
				.filter((ref) -> ref.getName().contains(branchName))
				.map((ref) -> new BranchProxy(git, ref))
				.findAny().get();
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}

	public List<TagModel> getTags() throws GitException {
		try {
			return git.tagList()
				.call().stream()
				.map(MAP_REF_TO_TAG)
				.collect(Collectors.toList());
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
	public CommitProxy getCommit(final String commitId) throws GitException {
		try {
			final RevCommit revCommit = git.log()
				.add(Commit.fromString(commitId))
				.setMaxCount(1)
				.call()
				.iterator().next();
			
			DetailedCommitModel model = MAP_REV_TO_DCOMMIT.apply(revCommit);
			return new CommitProxyImpl(git, model);
		}
		catch (MissingObjectException | IncorrectObjectTypeException
				| GitAPIException e) {
			throw new GitException(e);
		}
	}

	public void delete() {
		final File dir = repo.getDirectory();
		git.close();
		dir.delete();
	}

}
