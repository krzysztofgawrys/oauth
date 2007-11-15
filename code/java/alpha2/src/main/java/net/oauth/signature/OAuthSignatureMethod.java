/*
 * Copyright 2007 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.oauth.signature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.oauth.OAuth;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import org.apache.commons.codec.binary.Base64;

/** A pair of algorithms for computing and verifying an OAuth digital signature. */
public abstract class OAuthSignatureMethod {

    public static void sign(OAuthMessage message, OAuthConsumer consumer,
	    String tokenSecret) throws Exception {
	OAuthSignatureMethod signer = OAuthSignatureMethod.newMethod(message
		.getParameter("oauth_signature_method"), consumer);
	signer.setTokenSecret(tokenSecret);
	signer.sign(message);
    }

    public void sign(OAuthMessage message) throws Exception {
	message.addParameter(new OAuth.Parameter("oauth_signature",
		getSignature(message)));
    }

    public boolean verify(OAuthMessage message) throws Exception {
	return verify(message.getSignature(), getBaseString(message));
    }

    String getSignature(OAuthMessage message) throws Exception {
	return sign(getBaseString(message));
    }

    protected void initialize(String name, OAuthConsumer consumer)
	    throws Exception {
	String secret = consumer.consumerSecret;
	if (name.endsWith(_ACCESSOR)) {
	    Object accessorSecret = consumer
		    .getProperty(OAuthConsumer.ACCESSOR_SECRET);
	    if (accessorSecret != null) {
		secret = accessorSecret.toString();
	    }
	}
	if (secret == null) {
	    secret = "";
	}
	setConsumerSecret(secret);
    }

    /** Compute the signature for the given base string. */
    protected abstract String sign(String baseString) throws Exception;

    /**
         * Decide whether a signature is correct.
         * 
         * @return true if and only if the signature matches the base string.
         */
    protected abstract boolean verify(String signature, String baseString)
	    throws Exception;

    private String consumerSecret;

    private String tokenSecret;

    public String getConsumerSecret() {
	return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
	this.consumerSecret = consumerSecret;
    }

    public String getTokenSecret() {
	return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret) {
	this.tokenSecret = tokenSecret;
    }

    public String getBaseString(OAuthMessage message) throws IOException {
	return OAuth.percentEncode(message.httpMethod) //
		+ '&'
		+ OAuth.percentEncode(message.URL) //
		+ '&'
		+ OAuth.percentEncode(normalizeParameters(message
			.getParameters())) //
		+ '&' + OAuth.percentEncode(getConsumerSecret()) //
		+ '&' + OAuth.percentEncode(getTokenSecret());
    }

    public String normalizeParameters(Collection<? extends Map.Entry> parameters)
	    throws IOException {
	if (parameters == null) {
	    return "";
	}
	List<ComparableParameter> p = new ArrayList<ComparableParameter>(
		parameters.size());
	for (Map.Entry parameter : parameters) {
	    if (!"oauth_signature".equals(parameter.getKey())) {
		p.add(new ComparableParameter(parameter));
	    }
	}
	Collections.sort(p);
	return OAuth.formEncode(getParameters(p));
    }

    public static byte[] decodeBase64(String s) {
	return BASE64.decode(s.getBytes());
    }

    public static String base64Encode(byte[] b) {
	return new String(BASE64.encode(b));
    }

    private static final Base64 BASE64 = new Base64();

    /** The factory for signature methods. */
    public static OAuthSignatureMethod newMethod(String name,
	    OAuthConsumer consumer) throws Exception {
	Class methodClass = NAME_TO_CLASS.get(name);
	if (methodClass == null) {
	    throw new OAuthProblemException("signature_method_rejected");
	    // TODO: report oauth_acceptable_signature_methods
	}
	OAuthSignatureMethod method = (OAuthSignatureMethod) methodClass
		.newInstance();
	method.initialize(name, consumer);
	return method;
    }

    /**
         * Subsequently, newMethod(name) will attempt to instantiate the given
         * class (with no constructor parameters).
         */
    public static void registerMethodClass(String name, Class clazz) {
	NAME_TO_CLASS.put(name, clazz);
    }

    public static final String _ACCESSOR = "-Accessor";

    private static final Map<String, Class> NAME_TO_CLASS = new ConcurrentHashMap<String, Class>();
    static {
	registerMethodClass("HMAC-SHA1", HMAC_SHA1.class);
	registerMethodClass("PLAINTEXT", PLAINTEXT.class);
	registerMethodClass("HMAC-SHA1" + _ACCESSOR, HMAC_SHA1.class);
	registerMethodClass("PLAINTEXT" + _ACCESSOR, PLAINTEXT.class);
    }

    private static List<Map.Entry> getParameters(
	    Collection<ComparableParameter> parameters) {
	if (parameters == null) {
	    return null;
	}
	List<Map.Entry> list = new ArrayList<Map.Entry>(parameters.size());
	for (ComparableParameter parameter : parameters) {
	    list.add(parameter.value);
	}
	return list;
    }

    private static class ComparableParameter implements
	    Comparable<ComparableParameter> {

	ComparableParameter(Map.Entry value) {
	    this.value = value;
	    String n = toString(value.getKey());
	    String v = toString(value.getValue());
	    this.key = OAuth.percentEncode(n) + ' ' + OAuth.percentEncode(v);
	}

	final Map.Entry value;

	private final String key;

	private static final String toString(Object from) {
	    return (from == null) ? null : from.toString();
	}

	public int compareTo(ComparableParameter that) {
	    return this.key.compareTo(that.key);
	}

	@Override
	public String toString() {
	    return key;
	}

    }

}