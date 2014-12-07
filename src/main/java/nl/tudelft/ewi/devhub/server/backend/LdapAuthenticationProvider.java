package nl.tudelft.ewi.devhub.server.backend;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.devhub.server.Config;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;
import nl.tudelft.ewi.devhub.server.database.entities.User;

import org.apache.directory.api.ldap.codec.decorators.SearchResultEntryDecorator;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.message.BindRequest;
import org.apache.directory.api.ldap.model.message.BindRequestImpl;
import org.apache.directory.api.ldap.model.message.BindResponse;
import org.apache.directory.api.ldap.model.message.LdapResult;
import org.apache.directory.api.ldap.model.message.Response;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;

import com.google.common.collect.Lists;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Slf4j
@Singleton
public class LdapAuthenticationProvider implements AuthenticationProvider {
	
	private final Config config;
	private final LdapConnectionConfig ldapConfig;
	private final Provider<Users> userProvider;
	
	@Inject
	public LdapAuthenticationProvider(final Config config, final Provider<Users> userProvider) {
		this.config = config;
		this.ldapConfig = config.getLdapConnectionConfig();
		this.userProvider = userProvider;
	}

	@Override
	public AuthenticationSession authenticate(final String username, final String password)
			throws AuthenticationProviderUnavailable,
			InvalidCredentialsException {

		try {
			final LdapConnection connection = connect(username, password);
		
			return new AuthenticationSession() {
				
				@Override
				public void fetch(User user) throws IOException {
					try {
						List<LdapEntry> results = search(username, connection);
						
						if(!results.isEmpty()) {
							LdapEntry entry = results.get(0);
							user.setNetId(entry.getNetId());
							user.setName(entry.getName());
							user.setEmail(entry.getEmail());
							user.setPassword(password);
						}
					} catch (LdapException e) {
						throw new IOException(e);
					}
				}
				
				@Override
				public boolean synchronize(User user) throws IOException {
					if(!user.isPasswordMatch(password)) {
						user.setPassword(password);
						return true;
					}
					return false;
				}

				@Override
				public void close() throws IOException {
					connection.close();
				}
				
			};
		}
		catch (AuthenticationProviderUnavailable e) {
			return loginFromCache(username, password, e);
		}
		
	}
	
	/**
	 * If the {@link LdapConnection} couldn't be established due to a
	 * {@link AuthenticationProviderUnavailable} exception, try to login from
	 * cached credentials.
	 * 
	 * @param username
	 *            Username for the user
	 * @param password
	 *            Password
	 * @param cause
	 *            Original cause (why we entered this second try)
	 * @return A {@link AuthenticationSession} for the user
	 * @throws AuthenticationProviderUnavailable
	 *             Rethrows the original exception when the given credentials
	 *             couldn't be matched with the cached data
	 */
	private AuthenticationSession loginFromCache(String username,
			String password, AuthenticationProviderUnavailable cause)
			throws AuthenticationProviderUnavailable {
		
		try {
			User user = userProvider.get().findByNetId(username);

			if (user.isPasswordMatch(password)) {
				return new AbstractAuthenticationSession();
			} else {
				// Might be the correct password, we just couldn't fetch it from LDAP
				throw cause;
			}

		} catch (EntityNotFoundException e) {
			throw cause;
		}
	}
	
	/**
	 * Try to establish a {@link LdapConnection}
	 * 
	 * @param netId
	 *            Username for the user
	 * @param password
	 *            Password
	 * @return {@link LdapConnection}
	 * @throws InvalidCredentialsException
	 *             If the supplied credentials aren't correct
	 * @throws AuthenticationProviderUnavailable
	 *             If an error occurred while connecting to the LDAP server
	 */
	private LdapConnection connect(String netId, String password)
			throws InvalidCredentialsException,
			AuthenticationProviderUnavailable {
		
		LdapConnection conn = null;
		
		try {
			conn = new LdapNetworkConnection(ldapConfig);
			BindRequest request = new BindRequestImpl();
			request.setSimple(true);
			request.setName(netId + config.getLDAPExtension());
			request.setCredentials(password);
	
			BindResponse response = conn.bind(request);
			LdapResult ldapResult = response.getLdapResult();
			
			ResultCodeEnum resultCode = ldapResult.getResultCode();
			
			switch(resultCode){ 
			case SUCCESS:
				return conn;
			case INVALID_CREDENTIALS:
				conn.close();
				throw new InvalidCredentialsException();
			default:
				conn.close();
				throw new AuthenticationProviderUnavailable(ldapResult.getDiagnosticMessage());
			}
		}
		catch (LdapException | IOException e) {
			
			if(conn != null && conn.isConnected()) {
				try {
					conn.close();
				} catch(IOException e1) {
					log.info(e.getMessage(), e1);
				}
			}
			
			throw new AuthenticationProviderUnavailable(e);
		}
	}
	
	private String getValue(Entry entry, String key) throws LdapInvalidAttributeValueException {
		Attribute value = entry.get(key);
		if (value == null) {
			return null;
		}
		return value.getString();
	}
	
	private List<LdapEntry> search(String netId, LdapConnection conn) throws LdapInvalidDnException, LdapException {
		SearchRequest searchRequest = new SearchRequestImpl();
		searchRequest.setBase(new Dn(config.getLDAPPrimaryDomain()));
		searchRequest.setScope(SearchScope.SUBTREE);
		searchRequest.setFilter("(uid=" + netId + ")");

		SearchCursor cursor = null;
		try {
			cursor = conn.search(searchRequest);
			List<LdapEntry> entries = Lists.newArrayList();
			Iterator<Response> iterator = cursor.iterator();
			while (iterator.hasNext()) {
				Response response = iterator.next();
				SearchResultEntryDecorator decorator = (SearchResultEntryDecorator) response;

				Entry entry = decorator.getEntry();
				String id = getValue(entry, "uid");
				String email = getValue(entry, "mail");
				String name = getValue(entry, "displayName");
				if (name.contains(" - ")) {
					name = name.substring(0, name.indexOf(" - "));
				}

				entries.add(new LdapEntry(name, id, email));
			}

			Collections.sort(entries, new Comparator<LdapEntry>() {
				@Override
				public int compare(LdapEntry o1, LdapEntry o2) {
					String netId1 = o1.getNetId();
					String netId2 = o2.getNetId();
					return netId1.compareTo(netId2);
				}
			});
			return entries;
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Data
	static class LdapEntry {
		private final String name;
		private final String netId;
		private final String email;
	}

}
