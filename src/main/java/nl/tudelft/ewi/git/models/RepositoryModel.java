package nl.tudelft.ewi.git.models;

import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.hibernate.validator.constraints.NotEmpty;

/**
 * This model represents a view of a repository in the Gitolite config.
 *
 * @author michael
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RepositoryModel extends BaseModel {
	
	public static enum Level {
		ADMIN		("RW+"),
		READ_WRITE	("RW"),
		READ_ONLY	("R");
		
		public static Level getLevel(String value) {
			for (Level level : values()) {
				if (level.getLevel().equals(value)) {
					return level;
				}
			}
			return null;
		}
		
		private final String level;
		
		private Level(String level) {
			this.level = level;
		}
		
		public String getLevel() {
			return level;
		}
	}

	@NotEmpty
	private String name;

	private String url;
	
	@NotEmpty
	private Map<String, Level> permissions;
	
}
