package nl.tudelft.ewi.devhub.server.backend;

import java.util.List;

import com.google.inject.ImplementedBy;

import nl.tudelft.ewi.devhub.server.backend.LdapAuthenticationProvider.LdapEntry;

@ImplementedBy(PersistingLdapUserProcessor.class)
public interface LdapUserProcessor {
	void synchronize(String prefix, List<LdapEntry> entries);
}