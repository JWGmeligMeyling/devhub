package nl.tudelft.ewi.devhub.server.backend;

import java.util.List;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.backend.LdapAuthenticationProvider.LdapEntry;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;
import nl.tudelft.ewi.devhub.server.database.entities.User;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

@Slf4j
public class PersistingLdapUserProcessor implements LdapUserProcessor {

	private final Provider<UnitOfWork> workProvider;
	private final Provider<Users> users;

	@Inject
	PersistingLdapUserProcessor(Provider<UnitOfWork> workProvider, Provider<Users> users) {
		this.workProvider = workProvider;
		this.users = users;
	}

	@Override
	public void synchronize(String prefix, List<LdapEntry> entries) {
		UnitOfWork work = workProvider.get();
		try {
			work.begin();
			synchronizeInternally(prefix, entries);
		}
		catch (Throwable e) {
			log.error(e.getMessage(), e);
		}
		finally {
			work.end();
		}
	}

	@Transactional
	protected void synchronizeInternally(String prefix, List<LdapEntry> entries) {
		Users database = users.get();
		List<User> currentUsers = database.listAllWithNetIdPrefix(prefix);

		while (!currentUsers.isEmpty() || !entries.isEmpty()) {
			int compare = compareFirstItems(currentUsers, entries);
			if (compare == 0) {
				currentUsers.remove(0);
				LdapEntry ldapEntry = entries.remove(0);
				log.trace("User: {} already present in both LDAP and database", ldapEntry.getNetId());
			}
			else if (compare < 0) {
				User current = currentUsers.remove(0);
				log.trace("Removing user: {} since he/she is no longer present in LDAP", current.getNetId());
				database.delete(current);
			}
			else if (compare > 0) {
				LdapEntry entry = entries.remove(0);

				User user = new User();
				user.setNetId(entry.getNetId());
				user.setName(entry.getName());
				user.setEmail(entry.getEmail());

				log.trace("Persisting user: {} since he/she is present in LDAP", user.getNetId());
				database.persist(user);
			}
		}
	}

	private int compareFirstItems(List<User> fromDatabase, List<LdapEntry> fromLdap) {
		if (fromDatabase.isEmpty() && fromLdap.isEmpty()) {
			throw new IndexOutOfBoundsException();
		}
		else if (fromDatabase.isEmpty() && !fromLdap.isEmpty()) {
			return 1;
		}
		else if (!fromDatabase.isEmpty() && fromLdap.isEmpty()) {
			return -1;
		}

		User current = fromDatabase.get(0);
		LdapEntry entry = fromLdap.get(0);

		String netId1 = current.getNetId();
		String netId2 = entry.getNetId();
		return netId1.compareTo(netId2);
	}

}