package nl.tudelft.ewi.jgit.proxy;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import nl.tudelft.ewi.git.models.DetailedCommitModel;
import nl.tudelft.ewi.git.models.EntryType;

import org.eclipse.jgit.lib.ObjectLoader;

public interface CommitProxy {

	DetailedCommitModel getCommitModel();

	List<Diff> getDiff() throws GitException, IOException;

	List<Diff> getDiff(String other) throws GitException,
			IOException;

	Map<String, EntryType> showTree(String path) throws GitException,
			IOException;

	ObjectLoader showFile(String path) throws GitException, IOException;

}