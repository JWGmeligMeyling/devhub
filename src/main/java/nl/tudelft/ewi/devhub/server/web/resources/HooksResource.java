package nl.tudelft.ewi.devhub.server.web.resources;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.jaxrs.models.BuildRequest;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult.Status;
import nl.tudelft.ewi.build.jaxrs.models.GitSource;
import nl.tudelft.ewi.build.jaxrs.models.MavenBuildInstruction;
import nl.tudelft.ewi.devhub.server.Config;
import nl.tudelft.ewi.devhub.server.backend.BuildResultMailer;
import nl.tudelft.ewi.devhub.server.backend.BuildsBackend;
import nl.tudelft.ewi.devhub.server.database.controllers.BuildResults;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.BuildResult;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.web.filters.RequireAuthenticatedBuildServer;
import nl.tudelft.ewi.git.models.BranchModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.persist.Transactional;

@Slf4j
@Path("hooks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON + Resource.UTF8_CHARSET)
public class HooksResource extends Resource {

	@Data
	private static class GitPush {
		private String repository;
	}

	private final Config config;
	private final BuildsBackend buildBackend;
	private final BuildResults buildResults;
	private final Groups groups;
	private final BuildResultMailer mailer;

	@Inject
	HooksResource(Config config, BuildsBackend buildBackend, BuildResults buildResults,
			Groups groups, BuildResultMailer mailer) {

		this.config = config;
		this.buildBackend = buildBackend;
		this.buildResults = buildResults;
		this.groups = groups;
		this.mailer = mailer;
	}

	@POST
	@Path("git-push")
	public void onGitPush(@Context HttpServletRequest request, GitPush push) throws UnsupportedEncodingException {
		log.info("Received git-push event: {}", push);

//		Repositories repositories = client.repositories();
		DetailedRepositoryModel repository = null;//repositories.retrieve(push.getRepository());

		MavenBuildInstruction instruction = new MavenBuildInstruction();
		instruction.setWithDisplay(true);
		instruction.setPhases(new String[] { "package" });

		Group group = groups.findByRepoName(push.getRepository());
		for (BranchModel branch : repository.getBranches()) {
			if ("HEAD".equals(branch.getSimpleName())) {
				continue;
			}
			if (buildResults.exists(group, branch.getCommit())) {
				continue;
			}

			log.info("Submitting a build for branch: {} of repository: {}", branch.getName(), repository.getName());

			GitSource source = new GitSource();
			source.setRepositoryUrl(repository.getUrl());
			source.setBranchName(branch.getName());
			source.setCommitId(branch.getCommit());

			StringBuilder callbackBuilder = new StringBuilder();
			callbackBuilder.append(config.getHttpUrl());
			callbackBuilder.append("/hooks/build-result");
			callbackBuilder.append("?repository=" + URLEncoder.encode(repository.getName(), "UTF-8"));
			callbackBuilder.append("&commit=" + URLEncoder.encode(branch.getCommit(), "UTF-8"));

			BuildRequest buildRequest = new BuildRequest();
			buildRequest.setCallbackUrl(callbackBuilder.toString());
			buildRequest.setInstruction(instruction);
			buildRequest.setSource(source);
			buildRequest.setTimeout(group.getBuildTimeout());

			buildBackend.offerBuild(buildRequest);
			buildResults.persist(BuildResult.newBuildResult(group, branch.getCommit()));
		}
	}

	@POST
	@Path("build-result")
	@RequireAuthenticatedBuildServer
	@Transactional
	public void onBuildResult(@QueryParam("repository") String repository, @QueryParam("commit") String commit,
			nl.tudelft.ewi.build.jaxrs.models.BuildResult buildResult) throws UnsupportedEncodingException {

		String repoName = URLDecoder.decode(repository, "UTF-8");
		String commitId = URLDecoder.decode(commit, "UTF-8");
		Group group = groups.findByRepoName(repoName);

		BuildResult result;
		try {
			result = buildResults.find(group, commitId);
			result.setSuccess(buildResult.getStatus() == Status.SUCCEEDED);
			result.setLog(Joiner.on('\n')
				.join(buildResult.getLogLines()));

			buildResults.merge(result);
		}
		catch (EntityNotFoundException e) {
			result = BuildResult.newBuildResult(group, commitId);
			result.setSuccess(buildResult.getStatus() == Status.SUCCEEDED);
			result.setLog(Joiner.on('\n')
				.join(buildResult.getLogLines()));

			buildResults.persist(result);
		}

		if (!result.getSuccess()) {
			mailer.sendFailedBuildResult(Lists.newArrayList(Locale.ENGLISH), result);
		}
	}

}
