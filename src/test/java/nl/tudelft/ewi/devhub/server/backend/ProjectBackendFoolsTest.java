package nl.tudelft.ewi.devhub.server.backend;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.database.controllers.CourseEditions;
import nl.tudelft.ewi.devhub.server.database.controllers.TestDatabaseModule;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;
import nl.tudelft.ewi.devhub.server.database.entities.Course;
import nl.tudelft.ewi.devhub.server.database.entities.CourseEdition;
import nl.tudelft.ewi.devhub.server.database.entities.Group;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.web.errors.ApiError;
import nl.tudelft.ewi.devhub.webtests.rules.UnitOfWorkRule;
import nl.tudelft.ewi.git.models.CreateRepositoryModel;
import nl.tudelft.ewi.git.models.DetailedRepositoryModel;
import nl.tudelft.ewi.git.web.api.GroupApi;
import nl.tudelft.ewi.git.web.api.GroupsApi;
import nl.tudelft.ewi.git.web.api.RepositoriesApi;
import nl.tudelft.ewi.git.web.api.RepositoryApi;
import org.jukito.JukitoRunner;
import org.jukito.UseModules;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.*;

/**
 * @author Jan-Willem Gmelig Meyling
 */
@Slf4j
@RunWith(JukitoRunner.class)
@UseModules(TestDatabaseModule.class)
public class ProjectBackendFoolsTest extends BackendTest {

    @Inject
    RepositoriesApi repositoriesApi;

    @Inject
    GroupsApi groupsApi;

    @Inject
    Provider<ProjectsBackend> projectsBackendProvider;

    @Inject
    Provider<EntityManager> entityManagerProvider;

    @Inject
    Provider<UnitOfWork> unitOfWorkProvider;

    @Inject
    @Rule
    public UnitOfWorkRule unitOfWorkRule;

    CountDownLatch countDownLatch = new CountDownLatch(2);
    CountDownLatch middleCountDownLatch = new CountDownLatch(2);
    CountDownLatch countDownLatchEnd = new CountDownLatch(2);
    User user;
    CourseEdition course;

    @Before
    @Transactional
    public void createTwoScrumbagStudents() {
        user = new User();
        user.setName("Hoi");
        user.setNetId("Scrumbag");
        user.setEmail("scrumbag@tudelft.nl");
        entityManagerProvider.get().persist(user);
    }

    @Before
    @Transactional
    public void setupCourse() {
        course = createCourseEdition();
        entityManagerProvider.get().persist(course);
    }

    @Test
    public void thauShallPass() throws InterruptedException {
        RepositoryApi repositoryApi = mock(RepositoryApi.class);
        when(repositoriesApi.getRepository(anyString())).thenReturn(repositoryApi);
        when(groupsApi.getGroup(anyString())).thenReturn(mock(GroupApi.class));
        when(repositoriesApi.createRepository(any())).then(this::waitAndReturn);

        new Thread(this::runnableThing).start();
        new Thread(this::runnableThing).start();
        countDownLatch.await();

        System.out.println("What?");
        countDownLatchEnd.await();

        verify(repositoriesApi).createRepository(any());
        verify(repositoryApi, times(1)).deleteRepository();
    }

    @SneakyThrows
    private DetailedRepositoryModel waitAndReturn(InvocationOnMock invocationOnMock) {
        log.info("Faking repository creation, waiting for 1 second... {}", invocationOnMock.getArguments());
        Thread.sleep(1000);
        return null;
    }

    @SneakyThrows
    public void runnableThing() {
        UnitOfWork unitOfWork = unitOfWorkProvider.get();
        unitOfWork.begin();
        try {
            EntityManager em = entityManagerProvider.get();
            User user = em.find(
                User.class,
                this.user.getId()
            );

            CourseEdition courseEdition = em.find(
                CourseEdition.class,
                this.course.getId()
            );

            countDownLatch.countDown();
            middleCountDownLatch.countDown();
            middleCountDownLatch.await();

            projectsBackendProvider.get().setupProject(
                courseEdition,
                Collections.singleton(user)
            );
        }
        finally {
            countDownLatchEnd.countDown();
            unitOfWork.end();
        }
    }



}
