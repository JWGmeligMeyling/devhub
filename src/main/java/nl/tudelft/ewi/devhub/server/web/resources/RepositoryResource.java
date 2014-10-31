package nl.tudelft.ewi.devhub.server.web.resources;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.persist.Transactional;

import nl.tudelft.ewi.devhub.server.database.controllers.BuildResults;
import nl.tudelft.ewi.devhub.server.database.entities.Course;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.util.Highlight;
import nl.tudelft.ewi.devhub.server.web.errors.ApiError;
import nl.tudelft.ewi.devhub.server.web.filters.RequestScope;
import nl.tudelft.ewi.devhub.server.web.resources.ProjectsResource.CommitChecker;
import nl.tudelft.ewi.devhub.server.web.resources.ProjectsResource.Diff;
import nl.tudelft.ewi.devhub.server.web.resources.ProjectsResource.Pagination;
import nl.tudelft.ewi.devhub.server.web.templating.TemplateEngine;
import nl.tudelft.ewi.git.client.GitServerClient;
import nl.tudelft.ewi.git.client.Repositories;
import nl.tudelft.ewi.git.models.CommitModel;
import nl.tudelft.ewi.git.models.DetailedBranchModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;
import nl.tudelft.ewi.git.models.DiffModel;
import nl.tudelft.ewi.git.models.EntryType;

public class RepositoryResource extends Resource {

	private static final int PAGE_SIZE = 25;
	
	private final GitServerClient client;
	private final HttpServletRequest request;
	private final User user;
	private final Group group;
	private final Course course;
	private final DetailedRepositoryModel repository;
	private final BuildResults buildResults;
	private final TemplateEngine templateEngine;
	private final RequestScope scope;

	public RepositoryResource(GitServerClient client,
			HttpServletRequest request, User user, Group group, Course course,
			DetailedRepositoryModel repository, BuildResults buildResults,
			TemplateEngine templateEngine, RequestScope scope) {
		this.user = user;
		this.group = group;
		this.course = course;
		this.repository = repository;
		this.client = client;
		this.request = request;
		this.buildResults = buildResults;
		this.templateEngine = templateEngine;
		this.scope = scope;
	}
	
	@GET
	public Response showProjectOverview() throws ApiError, IOException {
		DetailedBranchModel branch;
		
		try {
			branch = client.repositories().retrieveBranch(repository, "master", 0, PAGE_SIZE);
		}
		catch (Throwable e) {
			if(!repository.getBranches().isEmpty()) {
				String branchName = repository.getBranches().iterator().next().getName();
				branch = fetchBranch(repository, branchName, 1);
			}
			else {
				branch = null; // no commits
			}
		}
		
		return showBranchOverview(request, group, repository, branch, 1);
	}
	
	@GET
	@Path("/branch/{branchName}")
	@Transactional
	public Response showBranchOverview(
			@PathParam("branchName") String branchName,
			@QueryParam("page") @DefaultValue("1") int page) throws ApiError, IOException {

		DetailedBranchModel branch = fetchBranch(repository, branchName, page);
		return showBranchOverview(request, group, repository, branch, page);
	}
	
	@GET
	@Path("/commits/{commitId}")
	public Response showCommitOverview(@Context HttpServletRequest request) {
		return redirect(request.getPathInfo() + "/diff");
	}
	
	@GET
	@Path("/commits/{commitId}/build")
	@Transactional
	public Response showCommitBuild(@PathParam("commitId") String commitId) throws IOException, ApiError {
		CommitModel commit = fetchCommitView(repository, commitId);

		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("group", group);
		parameters.put("commit", commit);
		parameters.put("states", new CommitChecker(group, buildResults));
		parameters.put("repository", repository);

		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-commit-view.ftl", locales, parameters));
	}
	

	@GET
	@Path("/commits/{commitId}/diff")
	@Transactional
	public Response showCommitChanges(@PathParam("commitId") String commitId)
			throws IOException, ApiError {
		return showDiff(commitId, null);
	}
	
	@GET
	@Path("/commits/{oldId}/diff/{newId}")
	@Transactional
	public Response showDiff( @PathParam("oldId") String oldId,
			@PathParam("newId") String newId)  throws IOException, ApiError {
		List<Diff> diffs = fetchDiffs(repository, newId, oldId);

		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("group", group);
		parameters.put("diffs", diffs);
		parameters.put("commit", fetchCommitView(repository, oldId));
		parameters.put("repository", repository);
		parameters.put("states", new CommitChecker(group, buildResults));

		if(newId != null) {
			CommitModel newCommit = fetchCommitView(repository, newId);
			parameters.put("newCommit", newCommit);
		}
		
		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-diff-view.ftl", locales, parameters));
	}
	
	@GET
	@Path("/commits/{commitId}/tree")
	@Transactional
	public Response getTree(@PathParam("commitId") String commitId)
					throws ApiError, IOException {
		return getTree(commitId, "");
	}

	
	@GET
	@Path("/commits/{commitId}/tree/{path:.+}")
	@Transactional
	public Response getTree(@PathParam("commitId") String commitId,
			@PathParam("path") String path) throws ApiError, IOException {
		
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
		
		entries.putAll(client.repositories().listDirectoryEntries(repository, commitId, path));
		
		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("commit", fetchCommitView(repository, commitId));
		parameters.put("path", path);
		parameters.put("group", group);
		parameters.put("repository", repository);
		parameters.put("entries", entries);
		parameters.put("states", new CommitChecker(group, buildResults));
		
		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-folder-view.ftl", locales, parameters));
	}
	
	@GET
	@Path("/commits/{commitId}/blob/{path:.+}")
	@Transactional
	public Response getBlob(@PathParam("commitId") String commitId,
			@PathParam("path") String path) throws ApiError, IOException {
		String folderPath = "";
		String fileName = path;
		if (path.contains("/")) {
			folderPath = path.substring(0, path.lastIndexOf('/'));
			fileName = path.substring(path.lastIndexOf('/') + 1);
		}
		
		DetailedRepositoryModel repository = fetchRepositoryView(group);
		Map<String, EntryType> entries = client.repositories().listDirectoryEntries(repository, commitId, folderPath);
		
		EntryType type = entries.get(fileName);
		
		if (type == EntryType.BINARY) {
			return Response.ok(client.repositories().showBinFile(repository, commitId, path))
					.header("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
					.build();
		}
		
		String[] contents = client.repositories().showFile(repository, commitId, path).split("\\r?\\n");
		
		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("commit", fetchCommitView(repository, commitId));
		parameters.put("path", path);
		parameters.put("contents", contents);
		parameters.put("highlight", Highlight.forFileName(path));
		parameters.put("group", group);
		parameters.put("repository", repository);
		parameters.put("states", new CommitChecker(group, buildResults));

		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-file-view.ftl", locales, parameters));
	}
	
	private Response showBranchOverview(HttpServletRequest request,
			Group group, DetailedRepositoryModel repository,
			DetailedBranchModel branch, int page) throws IOException {
		
		Map<String, Object> parameters = Maps.newLinkedHashMap();
		parameters.put("user", scope.getUser());
		parameters.put("group", group);
		parameters.put("states", new CommitChecker(group, buildResults));
		parameters.put("repository", repository);
		
		if(branch != null) {
			parameters.put("branch", branch);
			parameters.put("pagination", new Pagination(page, branch.getAmountOfCommits()));
		}
		
		List<Locale> locales = Collections.list(request.getLocales());
		return display(templateEngine.process("project-view.ftl", locales, parameters));
	}
	
	private DetailedRepositoryModel fetchRepositoryView(Group group) throws ApiError {
		try {
			Repositories repositories = client.repositories();
			return repositories.retrieve(group.getRepositoryName());
		}
		catch (Throwable e) {
			throw new ApiError("error.git-server-unavailable");
		}
	}
	
	private DetailedBranchModel fetchBranch(DetailedRepositoryModel repository,
			String branchName, int page) throws ApiError {
		try {
			return client.repositories().retrieveBranch(repository, branchName, (page - 1) * PAGE_SIZE, PAGE_SIZE);
		}
		catch (Throwable e) {
			throw new ApiError("error.git-server-unavailable");
		}
	}
	
	private CommitModel fetchCommitView(DetailedRepositoryModel repository, String commitId) throws ApiError {
		try {
			Repositories repositories = client.repositories();
			return repositories.retrieveCommit(repository, commitId);
		}
		catch (Throwable e) {
			throw new ApiError("error.git-server-unavailable");
		}
	}
	
	private List<Diff> fetchDiffs(DetailedRepositoryModel repository, String oldCommitId, String newCommitId) throws ApiError {
		try {
			Repositories repositories = client.repositories();
			List<Diff> result = Lists.newArrayList();
			List<DiffModel> diffs = repositories.listDiffs(repository, oldCommitId, newCommitId);
			
			for (DiffModel diff : diffs) {
				result.add(new Diff(diff));
			}
			
			return result;
		} catch (Throwable e) {
			throw new ApiError("error.git-server-unavailable");
		}
	}
	
}
