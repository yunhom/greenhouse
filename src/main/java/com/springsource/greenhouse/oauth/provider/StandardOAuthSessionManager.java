package com.springsource.greenhouse.oauth.provider;

import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.springframework.security.encrypt.SecureRandomStringKeyGenerator;

import com.springsource.greenhouse.account.AccountRepository;
import com.springsource.greenhouse.account.AppConnection;
import com.springsource.greenhouse.account.InvalidApiKeyException;

public class StandardOAuthSessionManager implements OAuthSessionManager {

	private final ConcurrentHashMap<String, StandardOAuthSession> sessions = new ConcurrentHashMap<String, StandardOAuthSession>();

	private final AccountRepository accountRepository;
	
	private final SecureRandomStringKeyGenerator keyGenerator = new SecureRandomStringKeyGenerator();

	@Inject
	public StandardOAuthSessionManager(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public OAuthSession newOAuthSession(String apiKey, String callbackUrl) {
		StandardOAuthSession session = new StandardOAuthSession(apiKey, callbackUrl, keyGenerator.generateKey(), keyGenerator.generateKey());
		sessions.put(session.getRequestToken(), session);
		return session;
	}

	public OAuthSession getSession(String requestToken) throws InvalidRequestTokenException {
		OAuthSession session = sessions.get(requestToken);
		if (session == null) {
			throw new InvalidRequestTokenException(requestToken);
		}
		return session;
	}

	public OAuthSession authorize(String requestToken, Long authorizingAccountId, String verifier) throws InvalidRequestTokenException {
		StandardOAuthSession session = getStandardSession(requestToken);
		if (session.authorized()) {
			throw new IllegalStateException("OAuthSession is already authorized");
		}
		session.authorize(authorizingAccountId, verifier);
		return session;
	}

	public AppConnection grantAccess(String requestToken) throws InvalidRequestTokenException {
		StandardOAuthSession session = getStandardSession(requestToken);
		if (!session.authorized()) {
			throw new IllegalStateException("OAuthSession is not yet authorized");
		}
		try {
			AppConnection connection = accountRepository.connectApp(session.getAuthorizingAccountId(), session.getApiKey());
			sessions.remove(requestToken);
			return connection;
		} catch (InvalidApiKeyException e) {
			throw new IllegalStateException("Unable to grant access due to session - have the App's key changed?", e);
		}
	}
	
	// internal helpers
	
	private StandardOAuthSession getStandardSession(String requestToken) throws InvalidRequestTokenException {
		return (StandardOAuthSession) getSession(requestToken);
	}
	
	private static class StandardOAuthSession implements OAuthSession {

		private String apiKey;
		
		private String callbackUrl;
		
		private String requestToken;
		
		private String secret;
		
		private Long authorizingAccountId;
		
		private String verifier;
		
		public StandardOAuthSession(String apiKey, String callbackUrl, String requestToken, String secret) {
			this.apiKey = apiKey;
			this.callbackUrl = callbackUrl;
			this.requestToken = requestToken;
			this.secret = secret;
		}

		public String getApiKey() {
			return apiKey;
		}

		public String getCallbackUrl() {
			return callbackUrl;
		}

		public String getRequestToken() {
			return requestToken;
		}

		public String getSecret() {
			return secret;
		}

		public void authorize(Long authorizingAccountId, String verifier) {
			this.authorizingAccountId = authorizingAccountId;
			this.verifier = verifier;
		}

		public boolean authorized() {
			return authorizingAccountId != null;
		}

		public Long getAuthorizingAccountId() {
			return authorizingAccountId;
		}

		public String getVerifier() {
			return verifier;
		}

	}

}