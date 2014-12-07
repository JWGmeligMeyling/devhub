package nl.tudelft.ewi.jgit.proxy;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.git.models.DiffModel;
import nl.tudelft.ewi.git.models.EntryType;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

@Slf4j
public class CommitProxy extends AbstractGitProxy {

	private final RevCommit revCommit;
	
	public CommitProxy(final Git git, final RevCommit revCommit) {
		super(git);
		Preconditions.checkNotNull(revCommit);
		this.revCommit = revCommit;
	}
	
	public String getCommit() {
		 return revCommit.getId().getName();
	}
	
	public String[] getParents() {
		final RevCommit[] parents = revCommit.getParents();
		final String[] parentIds = new String[parents.length];
		for (int i = 0; i < parents.length; i++) {
			ObjectId parentId = parents[i].getId();
			parentIds[i] = parentId.getName();
		}
		return parentIds;
	}
	
	public String getAuthor() {
		PersonIdent person = revCommit.getAuthorIdent();
		return String.format("%s <%s>", person.getName(), person.getEmailAddress());
	}
	
	public long getTime() {
		return revCommit.getCommitTime();
	}
	
	public String getMessage() {
		return revCommit.getShortMessage();
	}
	
	public String getFullMessage() {
		return revCommit.getFullMessage();
	}
	
	@JsonIgnore
	public String getMessageTail() {
		int index;
		String substring, fullMessage = getFullMessage();
		
		if((index = fullMessage.indexOf('\n')) != -1) {
			substring = fullMessage.substring(index);
			substring = removePrecedingNewLines(substring);
		}
		else {
			substring = "";
		}
		
		return substring;
	}
	
	private static String removePrecedingNewLines(String substring) {
		while(substring.charAt(0) == '\n') {
			if(substring.length() == 1 ) {
				substring = "";
				break;
			}
			substring = substring.substring(1);
		}
		return substring;
	}
	
	public List<Diff> getDiff() throws GitException, IOException {
		return getDiff((ObjectId) null);
	}
	
	public List<Diff> getDiff(String ref) throws GitException, IOException  {
		if(ref == null) {
			return getDiff((ObjectId) null);
		}
		return getDiff(repo.resolve(ref));
	}
	
	public List<Diff> getDiff(ObjectId other) throws GitException, IOException {
		AbstractTreeIterator oldTree = new EmptyTreeIterator();
		AbstractTreeIterator newTree = createTreeParser(revCommit);
		
		if(Objects.isNull(other) && revCommit.getParentCount() > 0) {
			oldTree = createTreeParser(revCommit.getParent(0));
		}
		
		final StoredConfig config = repo.getConfig();
		config.setString("diff", null, "algorithm", "histogram");
		
		try {
			
			List<DiffEntry> diffs = git.diff()
				.setContextLines(3)
				.setOldTree(oldTree)
				.setNewTree(newTree)
				.call();
			
			RenameDetector rd = new RenameDetector(repo);
			rd.addAll(diffs);
			diffs = rd.compute();
			
			return diffs.stream()
				.map((input) -> {
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
					return new Diff(diff);
				})
				.collect(Collectors.toList());
			
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
	private AbstractTreeIterator createTreeParser(RevCommit revCommit) throws IOException {
		RevWalk walk = new RevWalk(repo);
		RevCommit commit = walk.parseCommit(revCommit);
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
	
	public Map<String, EntryType> showTree(String path) throws GitException, IOException {
		
		RevWalk walk = new RevWalk(repo);
		RevCommit commit = walk.parseCommit(revCommit);

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
	
	public ObjectLoader showFile(String path) throws GitException, IOException {

		RevWalk walk = new RevWalk(repo);
		RevCommit commit = walk.parseCommit(revCommit);

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
	
	public void tag(final User user, final String name, final String message) throws GitException {
		try {
			git.tag()
				.setName(name)
				.setMessage(message)
				.setObjectId(revCommit)
				.setTagger(new PersonIdent(user.getName(), user.getEmail()))
				.call();
		}
		catch (GitAPIException e) {
			throw new GitException(e);
		}
	}
	
}
