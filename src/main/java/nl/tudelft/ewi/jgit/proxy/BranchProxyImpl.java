package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.CommitModel;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.DepthWalk.Commit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

public class BranchProxyImpl extends AbstractGitProxy implements BranchProxy {
	
	private final BranchModel branch;

	public BranchProxyImpl(final Git git, final BranchModel branch) {
		super(git);
		this.branch = branch;
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.BranchProxy#getBranchModel()
	 */
	@Override
	public BranchModel getBranchModel() {
		return branch;
	}

	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.BranchProxy#getCommits()
	 */
	@Override
	public List<CommitModel> getCommits(final int skip, final int limit) throws GitException {
		final ObjectId commit = Commit.fromString(branch.getCommit());
		
		try {
			ImmutableList.Builder<CommitModel> builder = new ImmutableList.Builder<CommitModel>();

			git.log()
				.add(commit)
				.setSkip(skip)
				.setMaxCount(limit)
				.call()
				.forEach(rev -> {
					builder.add(MAP_REV_TO_COMMIT.apply(rev));
				});

			return builder.build();
		}
		catch (MissingObjectException | IncorrectObjectTypeException | GitAPIException e) {
			throw new GitException(e);
		}
	}

	@Override
	public int amontOfCommits() throws GitException {
		final ObjectId commit = Commit.fromString(branch.getCommit());
		try {
			return Iterators.size(git.log()
				.add(commit)
				.call().iterator());
		}
		catch (MissingObjectException | IncorrectObjectTypeException
				| GitAPIException e) {
			throw new GitException(e);
		}
	}
	
}
