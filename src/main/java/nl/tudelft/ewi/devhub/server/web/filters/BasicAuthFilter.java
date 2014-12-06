package nl.tudelft.ewi.devhub.server.web.filters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.StringTokenizer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import nl.tudelft.ewi.devhub.server.backend.AuthenticationBackend;
import nl.tudelft.ewi.devhub.server.database.controllers.Users;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
@javax.ws.rs.ext.Provider
public class BasicAuthFilter implements Filter {

	private final AuthenticationBackend authenticationBackend;
	private final Provider<Users> usersProvider;

	private String realm = "Protected";

	@Inject
	public BasicAuthFilter(final AuthenticationBackend authenticationBackend,
			final Provider<Users> usersProvider) {
		this.authenticationBackend = authenticationBackend;
		this.usersProvider = usersProvider;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		String paramRealm = filterConfig.getInitParameter("realm");
		if (StringUtils.isNotBlank(paramRealm)) {
			realm = paramRealm;
		}
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		String authHeader = request.getHeader("Authorization");
		if (authHeader != null) {
			StringTokenizer st = new StringTokenizer(authHeader);
			if (st.hasMoreTokens()) {
				String basic = st.nextToken();

				if (basic.equalsIgnoreCase("Basic")) {
					try {
						String credentials = new String(Base64.decodeBase64(st.nextToken()), "UTF-8");
						int p = credentials.indexOf(":");
						if (p != -1) {
							String netId = credentials.substring(0, p).trim();
							String password = credentials.substring(p + 1).trim();
							
							if(authenticationBackend.authenticate(netId, password)) {
								request.setAttribute("user", usersProvider.get().findByNetId(netId));
							}
							else {
								unauthorized(response, "Bad credentials");
							}

						} else {
							unauthorized(response, "Invalid authentication token");
						}
						chain.doFilter(servletRequest, servletResponse);
					} catch (UnsupportedEncodingException e) {
						throw new Error("Couldn't retrieve authentication", e);
					}
				}
			}
		} else {
			unauthorized(response);
		}
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	private void unauthorized(HttpServletResponse response, String message)
			throws IOException {
		response.setHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
		response.sendError(401, message);
	}

	private void unauthorized(HttpServletResponse response) throws IOException {
		unauthorized(response, "Unauthorized");
	}

}
