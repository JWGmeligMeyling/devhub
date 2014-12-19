package nl.tudelft.ewi.devhub.server.web.resources;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.Data;

import nl.tudelft.ewi.devhub.server.RepositoryUrlResolver;
import nl.tudelft.ewi.devhub.server.database.controllers.BuildResults;
import nl.tudelft.ewi.devhub.server.database.controllers.Groups;
import nl.tudelft.ewi.devhub.server.database.entities.BuildResult;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.util.Highlight;
import nl.tudelft.ewi.devhub.server.web.errors.ApiError;
import nl.tudelft.ewi.devhub.server.web.errors.UnauthorizedException;
import nl.tudelft.ewi.devhub.server.web.filters.RequestScope;
import nl.tudelft.ewi.devhub.server.web.filters.RequireAuthenticatedUser;
import nl.tudelft.ewi.devhub.server.web.templating.TemplateEngine;
import nl.tudelft.ewi.git.models.EntryType;
import nl.tudelft.ewi.jgit.proxy.BranchProxy;
import nl.tudelft.ewi.jgit.proxy.CommitProxy;
import nl.tudelft.ewi.jgit.proxy.Diff;
import nl.tudelft.ewi.jgit.proxy.GitBackend;
import nl.tudelft.ewi.jgit.proxy.GitException;
import nl.tudelft.ewi.jgit.proxy.RepositoryProxy;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;

@RequestScoped
@Path("projects/{repoName:(courses(\\/[\\w\\-]+){2})|([\\w\\-]+\\/[\\w\\-]+)}")
@Produces(MediaType.TEXT_HTML + Resource.UTF8_CHARSET)
@RequireAuthenticatedUser
public class ProjectResource extends Resource {
	
	private static final int PAGE_SIZE = 25;

	private final TemplateEngine templateEngine;
	private final GitBackend gitBackend;
	private final Groups groups;
	private final RequestScope scope;
	private final BuildResults buildResults;
	private final RepositoryUrlResolver repoNameResolver;

	@Inject
	ProjectResource(TemplateEngine templateEngine, Groups groups,
			GitBackend gitBackend, RequestScope scope,
			BuildResults buildResults, RepositoryUrlResolver repoNameResolver) {

		this.templateEngine = templateEngine;
		this.groups = groups;
		this.gitBackend = gitBackend;
		this.scope = scope;
		this.buildResults = buildResults;
		this.repoNameResolver = repoNameResolver;
	}
	
	@GET
	@Transactional
	public Response showProjectOverview(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@QueryParam("fatal") String fatal) throws IOException, ApiError {

		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);
		
		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {
			BranchProxy branch = null;
			
			try {
				branch = repository.getBranch("master");
			}
			catch(Throwable t) {
				if(!repository.getBranches().isEmpty()) {
					branch = repository.getBranches().iterator().next();
				}
			}
			
			return showBranchOverview(request, group, repository, branch, 1);
		}
		catch(GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
	}
	
	@GET
	@Path("/branch/{branchName:.+}")
	@Transactional
	public Response showBranchOverview(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("branchName") String branchName,
			@QueryParam("page") @DefaultValue("1") int page,
			@QueryParam("fatal") String fatal) throws IOException, ApiError {

		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);
		
		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {

			BranchProxy branch = repository.getBranch(branchName);
			return showBranchOverview(request, group, repository, branch, page);
			
		}
		catch (GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
	}
	
	private Response showBranchOverview(HttpServletRequest request,
			Group group, RepositoryProxy repository,
			BranchProxy branch, int page) throws IOException, GitException {
		Preconditions.checkArgument(page > 0);
		
		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("group", group);
		parameters.put("states", new CommitChecker(group, buildResults));
		parameters.put("repository", repository);
		parameters.put("cloneUrl", repoNameResolver.resolveSsh(scope.getUser()
				.getNetId(), repository.getPath()));
		
		if(branch != null) {
			parameters.put("branch", branch);
			parameters.put("commits", branch.getCommits((page - 1) * PAGE_SIZE, PAGE_SIZE));
			parameters.put("pagination", new Pagination(page, branch.amontOfCommits()));
		}
		
		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-view.ftl", locales, parameters));
	}
	
	@GET
	@Path("/commits/{commitId}")
	public Response showCommitOverview(@Context HttpServletRequest request) {
		return redirect(request.getPathInfo() + "/diff");
	}
	
	@GET
	@Path("/commits/{commitId}/build")
	@Transactional
	public Response showCommitBuild(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("commitId") String commitId,
			@QueryParam("fatal") String fatal) throws IOException, ApiError {

		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);

		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {
			
			CommitProxy commit = repository.getCommit(commitId);

			Map<String, Object> parameters = Maps.newLinkedHashMap();
			parameters.put("user", scope.getUser());
			parameters.put("group", group);
			parameters.put("commit", commit);
			parameters.put("states", new CommitChecker(group, buildResults));
			parameters.put("repository", repository);
			
			List<Locale> locales = Collections.list(request.getLocales());
			return display(templateEngine.process("project-commit-view.ftl", locales, parameters));
		}
		catch(GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
		
	}
	
	@GET
	@Path("/commits/{commitId}/diff")
	@Transactional
	public Response showCommitChanges(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("commitId") String commitId) throws IOException,
			ApiError {

		return showDiff(request, repoName, commitId, null);
	}

	@GET
	@Path("/commits/{oldId}/diff/{newId}")
	@Transactional
	public Response showDiff(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("oldId") String oldId, @PathParam("newId") String newId)
			throws ApiError, IOException {
		
		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);

		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {
			
			CommitProxy commitProxy = repository.getCommit(oldId);
			List<Diff> diffs = commitProxy.getDiff(newId);

			Map<String, Object> parameters = Maps.newLinkedHashMap();
			parameters.put("user", scope.getUser());
			parameters.put("group", group);
			parameters.put("diffs", diffs);
			parameters.put("commit", commitProxy);
			parameters.put("repository", repository);
			parameters.put("states", new CommitChecker(group, buildResults));
			
			if(newId != null) {
				CommitProxy newCommit = repository.getCommit(newId);
				parameters.put("newCommit", newCommit);
			}
			
			List<Locale> locales = Collections.list(request.getLocales());
			return display(templateEngine.process("project-diff-view.ftl", locales, parameters));
			
		}
		catch(GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
	}
	
	@GET
	@Path("/commits/{commitId}/tree")
	@Transactional
	public Response getTree(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("commitId") String commitId) throws ApiError,
			IOException {
		return getTree(request, repoName, commitId, "");
	}
	
	@GET
	@Path("/commits/{commitId}/tree/{path:.+}")
	@Transactional
	public Response getTree(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("commitId") String commitId,
			@PathParam("path") String path) throws ApiError, IOException {
		
		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);

		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {

			Map<String, EntryType> entries = new TreeMap<>(new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					if (o1.endsWith("/") && o2.endsWith("/")) {
						return o1.compareTo(o2);
					}
					else if (!o1.endsWith("/") && !o2.endsWith("/")) {
						return o1.compareTo(o2);
					}
					else if (o1.endsWith("/")) {
						return -1;
					}
					return 1;
				}
				
			});
			
			CommitProxy commit = repository.getCommit(commitId);
			entries.putAll(commit.showTree(path));
			
			Map<String, Object> parameters = Maps.newLinkedHashMap();
			parameters.put("user", scope.getUser());
			parameters.put("commit", commit);
			parameters.put("path", path);
			parameters.put("group", group);
			parameters.put("repository", repository);
			parameters.put("entries", entries);
			parameters.put("states", new CommitChecker(group, buildResults));
			
			List<Locale> locales = Collections.list(request.getLocales());
			return display(templateEngine.process("project-folder-view.ftl", locales, parameters));
			
		}
		catch(GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
	}
	
	@GET
	@Path("/commits/{commitId}/blob/{path:.+}")
	@Transactional
	public Response getBlob(@Context HttpServletRequest request,
			@PathParam("repoName") String repoName,
			@PathParam("commitId") String commitId,
			@PathParam("path") String path) throws ApiError, IOException {

		User user = scope.getUser();
		Group group = groups.findByRepoName(repoName);

		String folderPath = "";
		String fileName = path;
		if (path.contains("/")) {
			folderPath = path.substring(0, path.lastIndexOf('/'));
			fileName = path.substring(path.lastIndexOf('/') + 1);
		}
		
		try(RepositoryProxy repository = gitBackend.open(group.getRepositoryName()).as(user)) {

			CommitProxy commitProxy = repository.getCommit(commitId);
			Map<String, EntryType> entries = commitProxy.showTree(folderPath);
			
			EntryType type = entries.get(fileName);
			ObjectLoader file = commitProxy.showFile(path);
			
			if (type == EntryType.BINARY) {
				return Response.ok(file.openStream(), MediaType.APPLICATION_OCTET_STREAM)
						.header ("Content-Length", file.getSize())
						.build();
			}
			
			String[] contents = new String(file.getBytes()).split("\\r?\\n");
			
			Map<String, Object> parameters = Maps.newLinkedHashMap();
			parameters.put("user", scope.getUser());
			parameters.put("commit", commitProxy);
			parameters.put("path", path);
			parameters.put("contents", contents);
			parameters.put("highlight", Highlight.forFileName(path));
			parameters.put("group", group);
			parameters.put("repository", repository);
			parameters.put("states", new CommitChecker(group, buildResults));
			
			List<Locale> locales = Collections.list(request.getLocales());
			return display(templateEngine.process("project-file-view.ftl", locales, parameters));

		}
		catch(GitException e) {
			throw new ApiError("error.git-server-unavailable", e);
		}
		catch (ServiceNotAuthorizedException e) {
			throw new UnauthorizedException();
		}
		
	}
	
	@Data
	public static class CommitChecker {
		private final Group group;
		private final BuildResults buildResults;

		public boolean hasFinished(String commitId) {
			try {
				BuildResult buildResult = buildResults.find(group, commitId);
				return buildResult.getSuccess() != null;
			}
			catch (EntityNotFoundException e) {
				return false;
			}
		}

		public boolean hasStarted(String commitId) {
			try {
				buildResults.find(group, commitId);
				return true;
			}
			catch (EntityNotFoundException e) {
				return false;
			}
		}

		public boolean hasSucceeded(String commitId) {
			BuildResult buildResult = buildResults.find(group, commitId);
			return buildResult.getSuccess();
		}

		public String getLog(String commitId) {
			BuildResult buildResult = buildResults.find(group, commitId);
			return buildResult.getLog();
		}
	}
	
	
	@Data
	static public class Pagination {
				
		private final int page, total;
		
		public int getPageCount() {
			return (total + PAGE_SIZE - 1) / PAGE_SIZE;
		}
		
	}

}
