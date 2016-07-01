package org.ngrinder.script.model;

import java.util.Map;

/**
 * Created by NAVER on 2016. 7. 11..
 */
public class Request {

	private String url;
	private String method;
	private String responseState;
	private Map<String,Object> queryString;
	private Map<String,Object> perRequestHeaders;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getResponseState() {
		return responseState;
	}

	public void setResponseState(String responseState) {
		this.responseState = responseState;
	}

	public Map<String, Object> getPerRequestHeaders() {
		return perRequestHeaders;
	}

	public void setPerRequestHeaders(Map<String, Object> perRequestHeaders) {
		this.perRequestHeaders = perRequestHeaders;
	}

	public Map<String, Object> getQueryString() {
		return queryString;
	}

	public void setQueryString(Map<String, Object> queryString) {
		this.queryString = queryString;
	}
}
