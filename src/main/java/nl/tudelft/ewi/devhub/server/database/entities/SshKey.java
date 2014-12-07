package nl.tudelft.ewi.devhub.server.database.entities;

import java.io.Serializable;
import java.security.PublicKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.Buffer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
@Entity
@Table(name = "ssh_keys")
public class SshKey implements Comparable<SshKey>, Serializable {

	private static final long serialVersionUID = -3042359439848158669L;

	@Id
	@Column(name = "name")
	@Pattern(regexp = "^[a-zA-Z0-9]+$")
	private String name;
	
	@Id
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;
	
	@Lob
	@Column(name = "contents")
	@Pattern(regexp= "^ssh-rsa AAAA[0-9A-Za-z+/]+[=]{0,3}( [^@]+@[^@]+)?$")
	private String contents;
	
	@JsonIgnore
	public PublicKey getPublicKey() throws SshException {
		String[] parts = getContents().split(" ");
		String keypart = parts[1];
		final byte[] bin = Base64.decodeBase64(keypart);
		return new Buffer(bin).getRawPublicKey();
	}

	@Override
	public int compareTo(final SshKey o) {
		return getName().compareTo(o.getName());
	}
	
}
