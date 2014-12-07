package nl.tudelft.ewi.jgit.proxy;

import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jgit.diff.DiffEntry;

import nl.tudelft.ewi.git.models.TagModel;
import nl.tudelft.ewi.git.models.DiffModel.Type;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import com.google.common.base.Preconditions;

public abstract class AbstractGitProxy implements AutoCloseable {

	protected final Git git;
	protected final Repository repo;
	
	protected AbstractGitProxy(final Git git) {
		Preconditions.checkNotNull(git);
		this.git = git;
		this.repo = git.getRepository();
	}
	
	public final static Predicate<Ref> SKIP_HEAD = (ref) -> {
		return !ref.getName().equals("refs/remotes/origin/HEAD");
	};
	
	public final static Function<Ref, TagModel> MAP_REF_TO_TAG = (input) -> {
		final TagModel tag = new TagModel();
		ObjectId objectId = input.getPeeledObjectId();
		if (objectId == null) {
			objectId = input.getObjectId();
		}
	
		tag.setName(input.getName());
		tag.setCommit(objectId.getName());
		return tag;
	};
	
	public static Type forChangeType(DiffEntry.ChangeType changeType) {
		switch (changeType) {
		case ADD:
			return Type.ADD;
		case COPY:
			return Type.COPY;
		case DELETE:
			return Type.DELETE;
		case MODIFY:
			return Type.MODIFY;
		case RENAME:
			return Type.RENAME;
		default:
			throw new IllegalArgumentException("Cannot convert change type: " + changeType);
		}
	}
	
	public Repository getRepository() {
		return repo;
	}
	
	@Override
	public void close() {
		git.close();
	}
}
