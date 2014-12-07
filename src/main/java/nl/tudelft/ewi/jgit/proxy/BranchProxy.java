package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

public class BranchProxy extends AbstractGitProxy {
	
	private static final String REFS_HEADS = "refs/heads/";
	
	private final Ref ref;

	public BranchProxy(final Git git, final Ref ref) {
		super(git);
		Preconditions.checkNotNull(ref);
		this.ref = ref;
	}
	
	public List<CommitProxy> getCommits(final int skip, final int limit) throws GitException {
		Preconditions.checkArgument(skip >= 0);
		Preconditions.checkArgument(limit >= 0);
		
		final ObjectId commit = ref.getObjectId();
		
		try {
			ImmutableList.Builder<CommitProxy> builder = new ImmutableList.Builder<CommitProxy>();

			git.log()
				.add(commit)
				.setSkip(skip)
				.setMaxCount(limit)
				.call()
				.forEach(revCommit -> {
					builder.add(new CommitProxy(git, revCommit));
				});

			return builder.build();
		}
		catch (MissingObjectException | IncorrectObjectTypeException | GitAPIException e) {
			throw new GitException(e);
		}
	}

	public int amontOfCommits() throws GitException {
		final ObjectId commit = ref.getObjectId();
		
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
	
	public String getName() {
		return ref.getName();
	}
	
	public String getSimpleName() {
		String name = ref.getName();
		if(name.startsWith(REFS_HEADS))
			return name.substring(REFS_HEADS.length());
		else
			return name;
	}
	
	public String getCommit() {
		return ref.getObjectId().getName();
	}
	
}
