[#macro listTags repository commitId]
	[#list repository.getTags() as tag]
		[#if tag.getCommit() == commitId]
			[#if tag.getName()?starts_with("refs/tags/")]
<span class="label label-primary">${tag.getName()?substring("refs/tags/"?length)}</span>
			[#else]
<span class="label label-primary">${tag.getName()}</span>
			[/#if]
		[/#if]
	[/#list]
[/#macro]

[#import "macros.ftl" as macros]
[@macros.renderHeader i18n.translate("section.projects") /]
[@macros.renderMenu i18n user /]
		<div class="container">
		
			<ol class="breadcrumb">
				<li><a href="/projects">Projects</a></li>
				<li class="active">${group.getGroupName()}</li>
			</ol>

			<h4>Git clone URL</h4>
			<div class="well well-sm">
[#if repository?? && repository?has_content]
				<code>git clone ${repository.getUrl()}</code>
[#else]
				<code>Could not connect to the Git server!</code>
[/#if]
			</div>


[#if repository?? && repository?has_content && branch?? && branch?has_content]
	<span class="pull-right">
		<div class="btn-group">
			<button type="button" class="btn btn-default">
				<span class="octicon octicon-git-branch"></span>
				<span class="text-muted">${i18n.translate("branch.current")}:</span>
				${branch.getSimpleName()}
			</button>
			<button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown">
				<span class="caret"></span>
				<span class="sr-only">${i18n.translate("branch.switch")}</span>
			</button>
			<ul class="dropdown-menu" role="menu">
	[#list repository.getBranches() as b ]
				<li><a href="/projects/${group.course.code}/groups/${group.groupNumber}/branch/${b.getSimpleName()}">${b.getSimpleName()}</a></li>
	[/#list]
			</ul>
		</div>
	</span>
[/#if]
		
			<h4>Recent commits</h4>
			
			<table class="table table-bordered">
				<tbody>
[#if repository?? && repository?has_content]
	[#if branch?? && branch?has_content && commits?? && commits?has_content]
		[#list commits as commit]
					<tr>
			[#if states.hasStarted(commit.getCommit())]
				[#if states.hasFinished(commit.getCommit())]
					[#if states.hasSucceeded(commit.getCommit())]
						<td class="commit succeeded">
							<a href="/projects/${group.course.code}/groups/${group.groupNumber}/commits/${commit.getCommit()}/build">
								<span class="state glyphicon glyphicon-ok-circle" title="Build succeeded!"></span>
							</a>
					[#else]
						<td class="commit failed">
							<a href="/projects/${group.course.code}/groups/${group.groupNumber}/commits/${commit.getCommit()}/build">
								<span class="state glyphicon glyphicon-remove-circle" title="Build failed!"></span>
							</a>
					[/#if]
				[#else]
						<td class="commit running">
							<span class="state glyphicon glyphicon-align-justify" title="Build queued..."></span>
				[/#if]
			[#else]
						<td class="commit ignored">
							<span class="state glyphicon glyphicon-unchecked"></span>
			[/#if]
							<a href="/projects/${group.course.code}/groups/${group.groupNumber}/commits/${commit.getCommit()}/diff">
								<div class="comment">${commit.getMessage()} [@listTags repository commit.getCommit() /]</div>
								<div class="committer">${commit.getAuthor()}</div>
								<div class="timestamp" data-value="${(commit.getTime() * 1000)}">on ${(commit.getTime() * 1000)?number_to_datetime?string["EEEE dd MMMM yyyy HH:mm"]}</div>
							</a>
						</td>
					</tr>
		[/#list]
	[#else]
						<tr>
							<td class="muted">
								There are no commits in this repository yet!
							</td>
						</tr>
	[/#if]
[#else]
						<tr>
							<td class="muted">
								Could not connect to the Git server!
							</td>
						</tr>
[/#if]
				</tbody>
			</table>


[#if branch?? && branch?has_content && pagination?? ]
[#assign pageCount = pagination.getPageCount() ]
[#assign currentPage = pagination.getPage() ]
			<div class="text-center">
				<ul class="pagination pagination-lg">
	[#list 1..pageCount as pageNumber ]
		[#if pageNumber == currentPage ]
					<li class="active"><a href="/projects/${group.course.code}/groups/${group.groupNumber}/branch/${branch.getSimpleName()}?page=${pageNumber}">${pageNumber}</a></li>
		[#else]
					<li><a href="/projects/${group.course.code}/groups/${group.groupNumber}/branch/${branch.getSimpleName()}?page=${pageNumber}">${pageNumber}</a></li>
		[/#if]
	[/#list]
				</ul>
			</div>
[/#if]

		</div>
[@macros.renderScripts /]
[@macros.renderFooter /]
