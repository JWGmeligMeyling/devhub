package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;
import nl.tudelft.ewi.git.models.TagModel;

public interface RepositoyProxy extends AutoCloseable {

	 List<BranchModel> getBranches() throws GitException;

	 BranchProxy getBranch(String branchName)
			throws GitException;

	 List<TagModel> getTags() throws GitException;

	 CommitProxy getCommit(String commitId) throws GitException;
	
	 void delete();

	 DetailedRepositoryModel getRepositoryModel() throws GitException;
	 
	 void close();

}