package nl.tudelft.ewi.jgit.proxy;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.DetailedCommitModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;
import nl.tudelft.ewi.git.models.TagModel;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;
import org.eclipse.jgit.revwalk.RevCommit;

public class RepositoryProxyImpl extends AbstractGitProxy implements RepositoyProxy {
	
	private final String path;
	
	public RepositoryProxyImpl(final Git git, String path) {
		super(git);
		this.path = path;
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.RepositoyProxy#getBranches()
	 */
	@Override
	public List<BranchModel> getBranches() throws GitException {
		try {
			return git.branchList()
				.call().stream()
				.filter(SKIP_HEAD)
				.map(MAP_REF_TO_BRANCH)
				.collect(Collectors.toList());
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.RepositoyProxy#getBranch(java.lang.String)
	 */
	@Override
	public BranchProxy getBranch(final String branchName) throws GitException {
		try {
			BranchModel branchModel =  git.branchList()
				.call().stream()
				.filter((ref) -> ref.getName().contains(branchName))
				.map(MAP_REF_TO_BRANCH)
				.findAny().get();
			return new BranchProxyImpl(git, branchModel);
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}

	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.RepositoyProxy#getTags()
	 */
	@Override
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
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.RepositoyProxy#getCommit(java.lang.String)
	 */
	@Override
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

	@Override
	public void delete() {
		final File dir = repo.getDirectory();
		git.close();
		dir.delete();
	}

	@Override
	public DetailedRepositoryModel getRepositoryModel() throws GitException {
		DetailedRepositoryModel model = new DetailedRepositoryModel();
		model.setBranches(getBranches());
		model.setTags(getTags());
		model.setName(path);
		model.setUrl(path);
		return model;
	}

}
