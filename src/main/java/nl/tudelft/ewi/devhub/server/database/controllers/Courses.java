package nl.tudelft.ewi.devhub.server.database.controllers;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import nl.tudelft.ewi.devhub.server.database.entities.Course;
import nl.tudelft.ewi.devhub.server.database.entities.QCourse;
import nl.tudelft.ewi.devhub.server.database.entities.QGroupMembership;
import nl.tudelft.ewi.devhub.server.database.entities.User;

import com.google.common.base.Preconditions;
import com.google.inject.persist.Transactional;
import com.mysema.query.jpa.impl.JPAQuery;

public class Courses extends Controller<Course> {

	@Inject
	public Courses(EntityManager entityManager) {
		super(entityManager);
	}

	@Transactional
	public Course find(long id) {
		return ensureNotNull(query().from(QCourse.course)
			.where(QCourse.course.id.eq(id))
			.singleResult(QCourse.course), "Could not find course with id: " + id);
	}

	@Transactional
	public Course find(String courseCode) {
		Preconditions.checkNotNull(courseCode);
		return ensureNotNull(query().from(QCourse.course)
			.where(QCourse.course.code.eq(courseCode))
			.where(QCourse.course.end.isNull())
			.singleResult(QCourse.course), "Could not find course with code: " + courseCode);
	}

	@Transactional
	public List<Course> listActiveCourses() {
		return query().from(QCourse.course)
			.where(QCourse.course.start.before(new Date()))
			.where(QCourse.course.end.isNull()
				.or(QCourse.course.end.after(new Date())))
			.orderBy(QCourse.course.code.asc())
			.orderBy(QCourse.course.name.asc())
			.orderBy(QCourse.course.start.asc())
			.list(QCourse.course);
	}

	@Transactional
	public List<Course> listNotYetParticipatedCourses(User user) {
		Preconditions.checkNotNull(user);
		Date now = new Date();

		List<Long> participatingCourses = query().from(QGroupMembership.groupMembership)
			.where(QGroupMembership.groupMembership.user.id.eq(user.getId()))
			.list(QGroupMembership.groupMembership.group.course.id);

		JPAQuery query = query().from(QCourse.course)
			.where(QCourse.course.start.before(now))
			.where(QCourse.course.end.isNull()
				.or(QCourse.course.end.after(now)));

		if (!participatingCourses.isEmpty()) {
			query = query.where(QCourse.course.id.notIn(participatingCourses));
		}

		return query.orderBy(QCourse.course.code.asc())
			.orderBy(QCourse.course.name.asc())
			.orderBy(QCourse.course.start.asc())
			.list(QCourse.course);
	}
}
