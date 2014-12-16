package nl.tudelft.ewi.devhub.server.web.resources;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import lombok.Data;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult.Status;
import nl.tudelft.ewi.devhub.server.backend.BuildResultMailer;
import nl.tudelft.ewi.devhub.server.database.controllers.BuildResults;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.BuildResult;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.web.filters.RequireAuthenticatedBuildServer;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.persist.Transactional;

@Path("hooks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON + Resource.UTF8_CHARSET)
public class HooksResource extends Resource {

	@Data
	private static class GitPush {
		private String repository;
	}

	private final BuildResults buildResults;
	private final Groups groups;
	private final BuildResultMailer mailer;

	@Inject
	HooksResource(final BuildResults buildResults, final Groups groups,
			final BuildResultMailer mailer) {

		this.buildResults = buildResults;
		this.groups = groups;
		this.mailer = mailer;
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
