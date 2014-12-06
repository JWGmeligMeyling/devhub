package nl.tudelft.ewi.jgit.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.diff.DiffEntry;

import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.DetailedCommitModel;
import nl.tudelft.ewi.git.models.DiffModel;
import nl.tudelft.ewi.git.models.TagModel;
import nl.tudelft.ewi.git.models.CommitModel;
import nl.tudelft.ewi.git.models.DiffModel.Type;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

@Slf4j
public abstract class AbstractGitProxy implements AutoCloseable {

	protected final Git git;
	protected final Repository repo;
	
	protected AbstractGitProxy(final Git git) {
		this.git = git;
		this.repo = git.getRepository();
	}
	
	public final static Predicate<Ref> SKIP_HEAD = (ref) -> {
		return !ref.getName().equals("refs/remotes/origin/HEAD");
	};
	
	public final static Function<Ref, BranchModel> MAP_REF_TO_BRANCH = (input) -> {
		final String name = input.getName();
		final ObjectId objectId = input.getObjectId();

		final BranchModel branch = new BranchModel();
		branch.setCommit(objectId.getName());
		branch.setName(name);
		return branch;
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
	
	public final static Function<RevCommit, CommitModel> MAP_REV_TO_COMMIT = (revCommit) -> {
		final RevCommit[] parents = revCommit.getParents();
		final String[] parentIds = new String[parents.length];
		for (int i = 0; i < parents.length; i++) {
			ObjectId parentId = parents[i].getId();
			parentIds[i] = parentId.getName();
		}

		final PersonIdent committerIdent = revCommit
				.getCommitterIdent();
		final ObjectId revCommitId = revCommit.getId();

		final CommitModel commit = new CommitModel();
		commit.setCommit(revCommitId.getName());
		commit.setParents(parentIds);
		commit.setTime(revCommit.getCommitTime());
		commit.setAuthor(committerIdent.getName(),
				committerIdent.getEmailAddress());
		commit.setMessage(revCommit.getShortMessage());

		return commit;
	};
	
	public final static Function<RevCommit, DetailedCommitModel> MAP_REV_TO_DCOMMIT = (revCommit) -> {
		final RevCommit[] parents = revCommit.getParents();
		final String[] parentIds = new String[parents.length];
		for (int i = 0; i < parents.length; i++) {
			ObjectId parentId = parents[i].getId();
			parentIds[i] = parentId.getName();
		}

		PersonIdent committerIdent = revCommit.getCommitterIdent();
		ObjectId revCommitId = revCommit.getId();

		DetailedCommitModel commit = new DetailedCommitModel();
		commit.setCommit(revCommitId.getName());
		commit.setParents(parentIds);
		commit.setTime(revCommit.getCommitTime());
		commit.setAuthor(committerIdent.getName(), committerIdent.getEmailAddress());
		commit.setMessage(revCommit.getShortMessage());
		commit.setFullMessage(revCommit.getFullMessage());

		return commit;
	};
	
	public final Function<DiffEntry, DiffModel> MapEntryToDiff() {
		return input -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			DiffFormatter formatter = new DiffFormatter(out);
			formatter.setRepository(repo);

			String contents = null;
			try {
				formatter.format(input);
				contents = out.toString("UTF-8");
			}
			catch (IOException e) {
				log.error(e.getMessage(), e);
			}

			DiffModel diff = new DiffModel();
			diff.setType(forChangeType(input.getChangeType()));
			diff.setOldPath(input.getOldPath());
			diff.setNewPath(input.getNewPath());
			diff.setRaw(contents.split("\\r?\\n"));
			return diff;
		};
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
