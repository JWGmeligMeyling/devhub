package nl.tudelft.ewi.devhub.server.web.resources;

import nl.tudelft.ewi.devhub.server.backend.CoursesBackend;
import nl.tudelft.ewi.devhub.server.database.controllers.CourseEditions;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;
import nl.tudelft.ewi.devhub.server.database.entities.CourseEdition;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.web.errors.UnauthorizedException;
import nl.tudelft.ewi.devhub.server.web.templating.TemplateEngine;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoped;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by jgmeligmeyling on 03/03/15.
 * @author Jan-Willem Gmelig Meyling
 */
@Path("courses/{courseCode}/{editionCode}/assistants")
@RequestScoped
@Produces(MediaType.TEXT_HTML + Resource.UTF8_CHARSET)
public class CourseAssistantsResource extends Resource {

    private final TemplateEngine templateEngine;
    private final CourseEditions courses;
    private final User currentUser;
    private final Users users;
    private final CoursesBackend coursesBackend;

    @Inject
    public CourseAssistantsResource(TemplateEngine templateEngine,
                                    CourseEditions courses,
                                    Users users,
                                    @Named("current.user") User currentUser,
                                    final CoursesBackend coursesBackend) {
        this.templateEngine = templateEngine;
        this.courses = courses;
        this.currentUser = currentUser;
        this.users = users;
        this.coursesBackend = coursesBackend;
    }

    @GET
    @Transactional
    public Response showProjectSetupPage(@Context HttpServletRequest request,
                                         @PathParam("courseCode") String courseCode,
                                         @PathParam("editionCode") String editionCode,
                                         @QueryParam("error") String error,
                                         @QueryParam("step") Integer step) throws IOException {

        CourseEdition courseEdition = courses.find(courseCode, editionCode);

        if(!currentUser.isAdmin()) {
            throw new UnauthorizedException();
        }

        if (step != null) {
            if (step == 1) {
                return showCourseAssistantsPageStep1(request, courseCode, editionCode, error);
            }
            else if (step == 2) {
                return showCourseAssistantsPageStep2(request, courseCode, editionCode, error);
            }
        }
        return redirect(courseEdition.getURI().resolve("assistants?step=1"));
    }

    private Response showCourseAssistantsPageStep1(@Context HttpServletRequest request,
                                                   @PathParam("courseCode") String courseCode,
                                                   @PathParam("editionCode") String editionCode,
                                                   @QueryParam("error") String error) throws IOException {


        HttpSession session = request.getSession();
        CourseEdition course = courses.find(courseCode, editionCode);

        String previousCourseCode = String.valueOf(session.getAttribute("courses.setup.course"));
        session.setAttribute("courses.setup.course", courseCode);
        if (!courseCode.equals(previousCourseCode)) {
            session.removeAttribute("courses.course.assistants");
        }

        Collection<User> members = (Collection<User>) session.getAttribute("courses.course.assistants");
        if(members == null)
            members = course.getAssistants();

        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("user", currentUser);
        parameters.put("course", course);
        if (members != null && !members.isEmpty()) {
            parameters.put("members", members);
        }
        if (!Strings.isNullOrEmpty(error)) {
            parameters.put("error", error);
        }

        List<Locale> locales = Collections.list(request.getLocales());
        return display(templateEngine.process("course-assistants-edit.ftl", locales, parameters));
    }

    @SuppressWarnings("unchecked")
    private Response showCourseAssistantsPageStep2(@Context HttpServletRequest request,
                                                   @PathParam("courseCode") String courseCode,
                                                   @PathParam("editionCode") String editionCode,
                                                   @QueryParam("error") String error) throws IOException {

        HttpSession session = request.getSession();
        CourseEdition course = courses.find(courseCode, editionCode);
        Collection<User> members = (Collection<User>) session.getAttribute("courses.course.assistants");

        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("user", currentUser);
        parameters.put("course", course);
        parameters.put("members", members);
        if (!Strings.isNullOrEmpty(error)) {
            parameters.put("error", error);
        }

        List<Locale> locales = Collections.list(request.getLocales());
        return display(templateEngine.process("course-assistants-confirm.ftl", locales, parameters));
    }

    @POST
    @SuppressWarnings("unchecked")
    public Response processProjectSetup(@Context HttpServletRequest request,
                                        @PathParam("courseCode") String courseCode,
										@PathParam("editionCode") String editionCode,
                                        @QueryParam("step") int step)
            throws IOException {

        if(!currentUser.isAdmin()) {
            throw new UnauthorizedException();
        }

        HttpSession session = request.getSession();
        CourseEdition course = courses.find(courseCode, editionCode);

        if (step == 1) {
            Collection<User> courseAssistants = getCourseAssistants(request);
            session.setAttribute("courses.course.assistants", courseAssistants);
            return redirect(course.getURI().resolve("assistants?step=2"));
        }

        Collection<User> courseAssistants = (Collection<User>) session.getAttribute("courses.course.assistants");
        coursesBackend.setAssistants(course, courseAssistants);

        session.removeAttribute("courses.course.assistants");
        return redirect(course.getURI());
    }

    private Collection<User> getCourseAssistants(HttpServletRequest request) {
        String netId;
        int memberId = 1;
        Set<String> netIds = Sets.newHashSet();
        while (!Strings.isNullOrEmpty((netId = request.getParameter("member-" + memberId)))) {
            memberId++;
            netIds.add(netId);
        }

        Map<String, User> members = users.mapByNetIds(netIds);
        return members.values();
    }

}
