package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.CommitModel;

public interface BranchProxy {

	BranchModel getBranchModel();

	List<CommitModel> getCommits(int skip, int limit) throws GitException;
	
	int amontOfCommits() throws GitException;

}