package nl.tudelft.ewi.devhub.server.database.entities;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.google.common.collect.Sets;

@Data
@Entity
@Table(name = "groups")
@EqualsAndHashCode(of = { "groupId" })
@ToString(of = { "groupId" })
public class Group implements Comparable<Group> {

	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long groupId;

	@NotNull
	@ManyToOne
	@JoinColumn(name = "course_id")
	private Course course;

	@NotNull
	@Column(name = "group_number")
	private long groupNumber;
	
	@Column(name = "build_timeout")
	private Integer buildTimeout;

	@OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
	private Set<GroupMembership> memberships;

	@NotNull
	@Column(name = "repository_name")
	private String repositoryName;

	public Set<User> getMembers() {
		Set<User> members = Sets.newHashSet();
		for (GroupMembership membership : memberships) {
			members.add(membership.getUser());
		}
		return members;
	}

	public String getGroupName() {
		StringBuilder builder = new StringBuilder();
		builder.append(course.getCode());
		builder.append(" - ");
		builder.append(course.getName());
		builder.append(" (Group #");
		builder.append(groupNumber);
		builder.append(")");
		return builder.toString();
	}

	@Override
	public int compareTo(Group group2) {
		Course course1 = getCourse();
		Course course2 = group2.getCourse();
		String code1 = course1.getCode();
		String code2 = course2.getCode();
		int compare = code1.compareTo(code2);
		if (compare != 0) {
			return compare;
		}
		
		return (int) (getGroupNumber() - group2.getGroupNumber());
	}

}
