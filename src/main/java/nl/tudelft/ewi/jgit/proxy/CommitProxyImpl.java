package nl.tudelft.ewi.jgit.proxy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.tudelft.ewi.git.models.DetailedCommitModel;
import nl.tudelft.ewi.git.models.DiffModel;
import nl.tudelft.ewi.git.models.EntryType;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

public class CommitProxyImpl extends AbstractGitProxy implements CommitProxy {

	private final DetailedCommitModel commit;
	
	public CommitProxyImpl(final Git git, final DetailedCommitModel commit) {
		super(git);
		this.commit = commit;
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.CommitProxy#getCommitModel()
	 */
	@Override
	public DetailedCommitModel getCommitModel() {
		return commit;
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.CommitProxy#getDiff()
	 */
	@Override
	public Collection<DiffModel> getDiff() throws GitException, IOException {
		return getDiff(null);
		
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.CommitProxy#getDiff(nl.tudelft.ewi.git.models.CommitModel)
	 */
	@Override
	public Collection<DiffModel> getDiff(String other) throws GitException, IOException {
		
		final StoredConfig config = repo.getConfig();
		config.setString("diff", null, "algorithm", "histogram");

		try {
			AbstractTreeIterator oldTreeIter = new EmptyTreeIterator();
			if (!Objects.isNull(other)) {
				oldTreeIter = createTreeParser( other);
			}
			AbstractTreeIterator newTreeIter = new EmptyTreeIterator();
			if (!Objects.isNull(commit)) {
				newTreeIter = createTreeParser(commit.getCommit());
			}

			List<DiffEntry> diffs = git.diff()
				.setContextLines(3)
				.setOldTree(oldTreeIter)
				.setNewTree(newTreeIter)
				.call();
			
			RenameDetector rd = new RenameDetector(repo);
			rd.addAll(diffs);
			diffs = rd.compute();
			
			return diffs.stream()
				.map(MapEntryToDiff())
				.collect(Collectors.toList());
			
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
	private AbstractTreeIterator createTreeParser(String ref) throws IOException, GitAPIException {
		RevWalk walk = new RevWalk(repo);
		RevCommit commit = walk.parseCommit(repo.resolve(ref));
		RevTree commitTree = commit.getTree();
		RevTree tree = walk.parseTree(commitTree.getId());

		CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
		ObjectReader oldReader = repo.newObjectReader();
		try {
			oldTreeParser.reset(oldReader, tree.getId());
		}
		finally {
			oldReader.release();
		}

		return oldTreeParser;
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.CommitProxy#showTree(java.lang.String)
	 */
	@Override
	public Map<String, EntryType> showTree(String path) throws GitException, IOException {
		
		RevWalk walk = new RevWalk(repo);
		ObjectId resolvedObjectId = repo.resolve(commit.getCommit());
		RevCommit commit = walk.parseCommit(resolvedObjectId);

		TreeWalk walker = new TreeWalk(repo);
		walker.setFilter(TreeFilter.ALL);
		walker.addTree(commit.getTree());
		walker.setRecursive(true);

		if (!path.endsWith("/") && !Strings.isNullOrEmpty(path)) {
			path += "/";
		}

		Map<String, EntryType> handles = Maps.newLinkedHashMap();
		while (walker.next()) {
			String entryPath = walker.getPathString();
			if (!entryPath.startsWith(path)) {
				continue;
			}

			String entry = entryPath.substring(path.length());
			if (entry.contains("/")) {
				entry = entry.substring(0, entry.indexOf('/') + 1);
			}
			
			handles.put(entry, of(repo, walker, entry));
		}

		if (handles.isEmpty()) {
			return null;
		}

		return handles;
		
	}
	
	private static EntryType of(org.eclipse.jgit.lib.Repository repo, TreeWalk walker, String entry) throws IOException {
		if (entry.endsWith("/")) {
			return EntryType.FOLDER;
		}

		ObjectId objectId = walker.getObjectId(0);
		ObjectLoader loader = repo.open(objectId);
		try (ObjectStream stream = loader.openStream()) {
			if (RawText.isBinary(stream)) {
				return EntryType.BINARY;
			}
			return EntryType.TEXT;
		}
	}
	
	/* (non-Javadoc)
	 * @see nl.tudelft.ewi.jgit.proxy.CommitProxy#showFile(org.eclipse.jgit.lib.Repository, java.lang.String, java.lang.String)
	 */
	@Override
	public ObjectLoader showFile(String path) throws GitException, IOException {

		RevWalk walk = new RevWalk(repo);
		ObjectId resolvedObjectId = repo.resolve(commit.getCommit());
		RevCommit commit = walk.parseCommit(resolvedObjectId);

		TreeWalk walker = new TreeWalk(repo);
		walker.setFilter(TreeFilter.ALL);
		walker.addTree(commit.getTree());
		walker.setRecursive(true);

		while (walker.next()) {
			String entryPath = walker.getPathString();
			if (!entryPath.startsWith(path)) {
				continue;
			}

			ObjectId objectId = walker.getObjectId(0);
			return repo.open(objectId);
		}

		throw new GitException(new FileNotFoundException(path));
	}
	
}
