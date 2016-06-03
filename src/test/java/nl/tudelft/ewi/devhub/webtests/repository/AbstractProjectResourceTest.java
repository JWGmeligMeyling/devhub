package nl.tudelft.ewi.devhub.webtests.repository;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import lombok.val;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.GroupRepository;
import nl.tudelft.ewi.devhub.server.web.resources.repository.ProjectResource;
import nl.tudelft.ewi.devhub.server.web.templating.TemplateEngine;
import nl.tudelft.ewi.git.models.CreateRepositoryModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;
import nl.tudelft.ewi.git.models.RepositoryModel;
import nl.tudelft.ewi.git.web.CloneStepDefinitions;
import nl.tudelft.ewi.git.web.CucumberModule;
import nl.tudelft.ewi.git.web.MergeStepDefinitions;
import nl.tudelft.ewi.git.web.api.RepositoriesApi;
import org.jukito.JukitoRunner;
import org.jukito.UseModules;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JukitoRunner.class)
@UseModules(CucumberModule.class)
public class AbstractProjectResourceTest {

    private static final String REPOSITORY_NAME = "JohnCena";
    private static final String BRANCH_NAME = "behindBranch";

    @Mock TemplateEngine templateEngine;
    @Mock HttpServletRequest request;

    @Captor ArgumentCaptor<Map<String, Object>> argumentCaptor;

    @Inject private RepositoriesApi repositoriesApi;
    @Inject private Injector injector;

    @Rule public MockitoJUnitRule mockitoJUnitRule = new MockitoJUnitRule(this);

    private CloneStepDefinitions cloneStepDefinitions;
    private MergeStepDefinitions mergeStepDefinitions;
    private DetailedRepositoryModel detailedRepositoryModel;
    private ProjectResource projectResource;

    @Before
    public void setUp() throws Throwable {
        cloneStepDefinitions = new CloneStepDefinitions();
        injector.injectMembers(cloneStepDefinitions);

        val crm = new CreateRepositoryModel();
        crm.setTemplateRepository("https://github.com/SERG-Delft/jpacman-template.git");
        crm.setName(REPOSITORY_NAME);
        crm.setPermissions(ImmutableMap.of("me", RepositoryModel.Level.ADMIN));
        detailedRepositoryModel = repositoriesApi.createRepository(crm);

        cloneStepDefinitions.iCloneRepository(REPOSITORY_NAME);

        Group group = new Group();
        GroupRepository groupRepository = new GroupRepository();
        groupRepository.setRepositoryName(REPOSITORY_NAME);
        group.setRepository(groupRepository);

        projectResource = new ProjectResource(templateEngine, null, group, null, null, null, repositoriesApi, null,
                null, null, null, null, null, null, null, null);

        Vector<Locale> vector = new Vector<>();
        when(request.getLocales()).thenReturn(vector.elements());

        mergeStepDefinitions = new MergeStepDefinitions();
        injector.injectMembers(mergeStepDefinitions);

    }

    @Test
    public void testDeleteBehindBranch() throws Throwable {
        cloneStepDefinitions.isAheadOf(BRANCH_NAME, "master");

        Response response = projectResource.deleteBehindBranch(request, BRANCH_NAME, "");
        verify(templateEngine).process(anyString(), anyObject(), argumentCaptor.capture());
        System.out.println("\n\n\n\n\n=======1:\n" + argumentCaptor.getValue());

    }

}
