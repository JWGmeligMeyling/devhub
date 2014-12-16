package nl.tudelft.ewi.jgit.proxy;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.jaxrs.models.BuildRequest;
import nl.tudelft.ewi.build.jaxrs.models.GitSource;
import nl.tudelft.ewi.build.jaxrs.models.MavenBuildInstruction;
import nl.tudelft.ewi.devhub.server.Config;
import nl.tudelft.ewi.devhub.server.RepositoryUrlResolver;
import nl.tudelft.ewi.devhub.server.backend.BuildsBackend;
import nl.tudelft.ewi.devhub.server.database.controllers.BuildResults;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.BuildResult;
import nl.tudelft.ewi.devhub.server.database.entities.Course;
import nl.tudelft.ewi.devhub.server.database.entities.Group;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

@Slf4j
public class BuildHook implements PostReceiveHook {
	
	private final Groups groups;
	private final BuildsBackend buildBackend;
	private final BuildResults buildResults; 
	private final Config config;
	private final RepositoryUrlResolver repositoryUrlResolver;
	private final RepositoryProxy repositoryProxy;
	
	@AssistedInject
	public BuildHook(final Groups groups, final BuildsBackend buildBackend,
			final BuildResults buildResults, final Config config,
			final RepositoryUrlResolver repositoryUrlResolver,
			@Assisted final RepositoryProxy repositoryProxy) {

		this.buildBackend = buildBackend;
		this.buildResults = buildResults;
		this.groups = groups;
		this.config = config;
		this.repositoryUrlResolver = repositoryUrlResolver;
		this.repositoryProxy = repositoryProxy;
	}
	
	public static interface BuildHookFactory {
		
		BuildHook create(RepositoryProxy repository);
		
	}

	@Override
	public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
		
		MavenBuildInstruction instruction = new MavenBuildInstruction();
		instruction.setWithDisplay(true);
		instruction.setPhases(new String[] { "package" });

		String refName = null;
		String commitId = null;
		
		for(ReceiveCommand command : commands) {
			refName = command.getRefName();
			commitId = command.getNewId().getName();
			break;
		}
		
		if(refName == null) {
			log.warn("No build was triggered", new NullPointerException("Ref name should not be null"));
			return;
		}
		else if(commitId == null) {
			log.warn("No build was triggered", new NullPointerException("CommitId should not be null"));
			return;
		}
		
		String path = repositoryProxy.getPath();
		GitSource source = new GitSource();
		source.setRepositoryUrl(repositoryUrlResolver.resolveSsh("jgmeligmeyling", path));
		source.setBranchName(refName);
		source.setCommitId(commitId);
		
		log.debug("Received push: {}", source);
		
		StringBuilder callbackBuilder = new StringBuilder();
		callbackBuilder.append(config.getHttpUrl()).append(":").append(config.getHttpPort());
		callbackBuilder.append("/hooks/build-result");
		
		try {
			callbackBuilder.append("?repository=" + URLEncoder.encode(path, "UTF-8"));
			callbackBuilder.append("&commit=" + URLEncoder.encode(commitId, "UTF-8"));
		}
		catch (UnsupportedEncodingException e ) {
			log.warn("No build was triggered", e);
			return;
		}
		
		Group group = groups.findByRepoName(path);
		Course course = group.getCourse();
		
		BuildRequest buildRequest = new BuildRequest();
		buildRequest.setCallbackUrl(callbackBuilder.toString());
		buildRequest.setInstruction(instruction);
		buildRequest.setSource(source);
		
		try {
			buildRequest.setTimeout(course.getBuildTimeout());
		}
		catch (NullPointerException e) {
			buildRequest.setTimeout(600);
		}
		
		BuildResult buildResult;
		
		try {
			// Update old build result
			buildResult = buildResults.find(group, commitId);
			buildResult.setSuccess(null);
			buildResult.setLog(null);
			buildResults.merge(buildResult);
		}
		catch (Throwable t) {
			// Create new build result
			buildResult = BuildResult.newBuildResult(group, commitId);
			buildResults.persist(buildResult);
		}
		
		log.info("Offering build request: {}", buildRequest);
		buildBackend.offerBuild(buildRequest);
	}

}
