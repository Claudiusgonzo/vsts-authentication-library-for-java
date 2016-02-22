// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.auth;

import com.microsoft.alm.auth.secret.Credential;
import com.microsoft.alm.auth.secret.Secret;
import com.microsoft.alm.auth.secret.Token;
import com.microsoft.alm.auth.secret.TokenPair;
import com.microsoft.alm.auth.secret.VsoTokenScope;
import com.microsoft.alm.storage.SecretStore;

import java.net.URI;

/**
 * No op authenticator with default implementations
 *
 * Real authenticator should extend this No Op authenticator and do not have to implement
 * methods that don't make sense to them
 */
public abstract class BaseAuthenticator implements Authenticator {

    protected Secret.IUriNameConversion uriToKeyConversion = Secret.DefaultUriNameConversion;

    @Override
    public boolean isCredentialSupported() {
        return false;
    }

    @Override
    public Credential getCredential(final URI key) {
        return null;
    }

    @Override
    public Credential getCredential(final URI key, final PromptBehavior promptBehavior) {
        return null;
    }

    @Override
    public boolean isOAuth2TokenSupported() {
        return false;
    }

    @Override
    public TokenPair getVstsGlobalOAuth2TokenPair(final PromptBehavior promptBehavior) {
        return null;
    }

    @Override
    public TokenPair getOAuth2TokenPair(final URI key) {
        return null;
    }

    @Override
    public TokenPair getOAuth2TokenPair(final URI key, final PromptBehavior promptBehavior) {
        return null;
    }

    @Override
    public boolean isPatSupported() {
        return false;
    }

    @Override
    public Token getVstsGlobalPat(final VsoTokenScope tokenScope, final String patDisplayName,
                                  final PromptBehavior promptBehavior) {
        return null;
    }

    @Override
    public Token getPersonalAccessToken(final URI key, final VsoTokenScope tokenScope,
                                        final String patDisplayName, final PromptBehavior promptBehavior) {
        return null;
    }

    @Override
    public boolean signOut(final URI uri) {
        final String key = getKey(uri);

        synchronized (getStore()) {
            return getStore().delete(key);
        }
    }

    /**
     * Keys are separated by name space, which are just the authentication type of this Authentcator
     *
     * So a PAT key will be different from an OAuth2 Key even for the same URI
     *
     * @param targetUri
     * @return key used to retrieve and store secrets in a secret store
     */
    public String getKey(final URI targetUri) {
        return this.uriToKeyConversion.convert(targetUri, getAuthType());
    }

    protected abstract SecretStore getStore();

    /**
     * Common pattern to retrieve a secret from store based on supplied prompt behavior
     *
     * TODO: should we also add validation behavior extension point here?
     */
    public static abstract class SecretRetriever {
        /**
         * Standard synchronized access to store.  Extensibility point that
         * can be overridden
         *
         * @param key
         * @param store
         * @return stored secret based on key, nullable
         */
        protected <E extends Secret> E readFromStore(final String key, final SecretStore<E> store) {
            synchronized (store) {
                return store.get(key);
            }
        }

        /**
         * How the secret is generated / retrieved.  This is the real work
         * @return secret
         */
        protected abstract <E extends Secret> E doRetrieve();

        /**
         * Standard storing the secret based on the key
         *
         * This is an extensibility point
         * @param secret
         */
        protected <E extends Secret> void store(final String key, final SecretStore<E> store, E secret) {
            if (secret != null) {
                synchronized (store) {
                    store.add(key, secret);
                }
            }
        }

        public <E extends Secret> E retrieve(final String key, final SecretStore<E> store,
                                                final PromptBehavior promptBehavior) {
            E secret = null;
            if (promptBehavior != PromptBehavior.ALWAYS) {
                // Not ALWAYS prompt, so let's read from the store for any cached secret
                secret = readFromStore(key, store);
            }

            if (promptBehavior == PromptBehavior.NEVER) {
                // NEVER prompt, return what we got from the store and call it done
                return secret;
            }

            if (promptBehavior == PromptBehavior.ALWAYS
                    || (secret == null && promptBehavior == PromptBehavior.AUTO)) {
                // Either ALWAYS prompt, or we don't have any secret cached for this key
                // AUTO-retrieves when necessary
                secret = doRetrieve();

                // Store it so we don't have to retrieve again
                store(key, store, secret);
            }

            return secret;
        }
    }
}