package nl.tudelft.ewi.jgit.proxy;

import java.util.List;

import lombok.Data;
import nl.tudelft.ewi.devhub.server.util.DiffLine;
import nl.tudelft.ewi.git.models.DiffModel;

@Data
public class Diff {
	
	private final List<DiffLine> lines;
	private final DiffModel diffModel;
	
	public Diff(DiffModel diffModel) {
		this.diffModel = diffModel;
		this.lines = DiffLine.getLinesFor(diffModel);
	}
	
	public static Diff of(DiffModel diffModel) {
		return new Diff(diffModel);
	}
	
	public boolean isDeleted() {
		return diffModel.getType().equals(DiffModel.Type.DELETE);
	}
	
	public boolean isAdded() {
		return diffModel.getType().equals(DiffModel.Type.ADD);
	}
	
	public boolean isModified() {
		return diffModel.getType().equals(DiffModel.Type.MODIFY);
	}
	
	public boolean isCopied() {
		return diffModel.getType().equals(DiffModel.Type.COPY);
	}
	
	public boolean isMoved() {
		return diffModel.getType().equals(DiffModel.Type.RENAME);
	}
	
}