package nl.tudelft.ewi.devhub.server.backend;

import java.util.List;

import javax.persistence.EntityNotFoundException;

import com.google.inject.Inject;

import nl.tudelft.ewi.devhub.server.database.controllers.SshKeys;
import nl.tudelft.ewi.devhub.server.database.entities.SshKey;
import nl.tudelft.ewi.devhub.server.database.entities.User;
import nl.tudelft.ewi.devhub.server.web.errors.ApiError;

public class SshKeyBackend {

	private static final String DUPLICATE_KEY = "error.duplicate-key";
	private static final String INVALID_KEY_CONTENTS = "error.invalid-key-contents";
	private static final String INVALID_KEY_NAME = "error.invalid-key-name";
	private static final String NAME_ALREADY_EXISTS = "error.name-alread-exists";
	private static final String NO_SUCH_KEY = "error.no-such-key";

	private final SshKeys sshKeys;

	@Inject
	SshKeyBackend(final SshKeys sshKeys) {
		this.sshKeys = sshKeys;
	}

	public void createNewSshKey(User user, String name, String contents) throws ApiError {
		if (name == null || !name.matches("^[a-zA-Z0-9]+$")) {
			throw new ApiError(INVALID_KEY_NAME);
		}
		if (contents == null || !contents.matches("^ssh-rsa\\s.+\\s*$")) {
			throw new ApiError(INVALID_KEY_CONTENTS);
		}
		
		for(SshKey key : sshKeys.getKeysFor(user)) {
			if(key.getContents().equals(contents)) {
				throw new ApiError(DUPLICATE_KEY);
			}
			else if (key.getName().equals(name)) {
				throw new ApiError(NAME_ALREADY_EXISTS);
			}
		}

		SshKey key = new SshKey();
		key.setUser(user);
		key.setName(name);
		key.setContents(contents);
		
		try {
			sshKeys.persist(key);
		}
		catch (Throwable t) {
			throw new ApiError(INVALID_KEY_CONTENTS, t);
		}
	}

	public void deleteSshKey(User user, String name) throws ApiError {
		if (name == null || !name.matches("^[a-zA-Z0-9]+$")) {
			throw new ApiError(INVALID_KEY_NAME);
		}
		
		try {
			SshKey key = sshKeys.getKey(user, name);
			sshKeys.delete(key);
		}
		catch (EntityNotFoundException e) {
			throw new ApiError(NO_SUCH_KEY, e);
		}
	}

	public List<SshKey> listKeys(User user) throws ApiError {
		return sshKeys.getKeysFor(user);
	}

}
