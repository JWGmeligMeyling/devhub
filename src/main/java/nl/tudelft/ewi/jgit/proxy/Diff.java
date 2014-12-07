package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import nl.tudelft.ewi.devhub.server.util.DiffLine;
import nl.tudelft.ewi.git.models.DiffModel;

@Data
@NoArgsConstructor
@EqualsAndHashCode( callSuper=true)
public class Diff extends DiffModel {
	
	private List<DiffLine> lines;
	
	public Diff(final DiffModel diffModel) {
		setNewPath(diffModel.getNewPath());
		setOldPath(diffModel.getOldPath());
		setType(diffModel.getType());
		setLines(DiffLine.getLinesFor(diffModel));
	}
	
	public static Diff of(DiffModel diffModel) {
		return new Diff(diffModel);
	}
	
	public boolean isDeleted() {
		return getType().equals(DiffModel.Type.DELETE);
	}
	
	public boolean isAdded() {
		return getType().equals(DiffModel.Type.ADD);
	}
	
	public boolean isModified() {
		return getType().equals(DiffModel.Type.MODIFY);
	}
	
	public boolean isCopied() {
		return getType().equals(DiffModel.Type.COPY);
	}
	
	public boolean isMoved() {
		return getType().equals(DiffModel.Type.RENAME);
	}
	
}