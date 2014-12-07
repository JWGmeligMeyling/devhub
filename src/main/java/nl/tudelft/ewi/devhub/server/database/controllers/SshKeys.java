package nl.tudelft.ewi.devhub.server.database.controllers;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import nl.tudelft.ewi.devhub.server.database.entities.QSshKey;
import nl.tudelft.ewi.devhub.server.database.entities.SshKey;
import nl.tudelft.ewi.devhub.server.database.entities.User;

public class SshKeys extends Controller<SshKey> {

	@Inject
	public SshKeys(EntityManager entityManager) {
		super(entityManager);
	}
	
	@Transactional
	public List<SshKey> getKeysFor(User user) {
		return query().from(QSshKey.sshKey)
			.where(QSshKey.sshKey.user.eq(user))
			.list(QSshKey.sshKey);
	}
	
	@Transactional
	public SshKey getKey(User user, String name) {
		SshKey key = query().from(QSshKey.sshKey)
			.where(QSshKey.sshKey.user.eq(user)
				.and(QSshKey.sshKey.name.eq(name)))
			.singleResult(QSshKey.sshKey);
		
		if(key == null) {
			throw new EntityNotFoundException("SshKey with name " + name
					+ " could not be found");
		}
		
		return key;
	}
	
}
