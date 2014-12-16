package nl.tudelft.ewi.devhub.server.database.entities;

import java.util.Collections;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.mindrot.jbcrypt.BCrypt;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

@Data
@Entity
@Table(name = "users")
@ToString(of = {"id", "name", "netId" })
@EqualsAndHashCode(of = { "netId" })
public class User {

	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NotNull
	@Column(name = "net_id")
	private String netId;

	@Column(name = "name")
	private String name;

	@Column(name = "email")
	private String email;
	
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	@Column(name = "password")
	private String password;
	
	@Column(name = "admin")
	private boolean admin;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private List<GroupMembership> memberOf;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
	private List<CourseAssistant> assists;

	public List<Group> listGroups() {
		List<Group> groups = Lists.newArrayList();
		for (GroupMembership membership : memberOf) {
			groups.add(membership.getGroup());
		}

		Collections.sort(groups);
		return groups;
	}
	
	public List<Group> listAssistedGroups() {
		List<Group> groups = Lists.newArrayList();
		for (CourseAssistant assist : assists) {
			groups.addAll(assist.getCourse().getGroups());
		}

		Collections.sort(groups);
		return groups;
	}

	public boolean isMemberOf(Group group) {
		for (GroupMembership membership : memberOf) {
			if (group.equals(membership.getGroup())) {
				return true;
			}
		}
		return false;
	}

	public boolean isAssisting(Course course) {
		for (CourseAssistant assistant : assists) {
			Course assistedCourse = assistant.getCourse();
			if (assistedCourse.getId() == course.getId()) {
				return true;
			}
		}
		return false;
	}

	public boolean isParticipatingInCourse(Course course) {
		for (GroupMembership membership : memberOf) {
			Group group = membership.getGroup();
			if (course.equals(group.getCourse())) {
				return true;
			}
		}
		return false;
	}
	
	public void setPassword(String password) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(password));
		this.password = BCrypt.hashpw(password, BCrypt.gensalt());
	}
	
	public boolean isPasswordMatch(String password) {
		return password != null && this.password != null
				&& BCrypt.checkpw(password, this.password);
	}
	
}
