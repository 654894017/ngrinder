import static net.grinder.script.Grinder.grinder
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import net.grinder.plugin.http.HTTPRequest
import net.grinder.plugin.http.HTTPPluginControl
import net.grinder.script.GTest
import net.grinder.script.Grinder
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
// import static net.grinder.util.GrinderUtils.* // You can use this if you're using nGrinder after 3.2.3
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

import java.util.Date
import java.util.List
import java.util.ArrayList

import HTTPClient.Cookie
import HTTPClient.CookieModule
import HTTPClient.HTTPResponse
import HTTPClient.NVPair

@RunWith(GrinderRunner)
class TestRunner {

	public static GTest test
	public static HTTPRequest request
	public static NVPair[] headers = []

	@BeforeProcess
	public static void beforeProcess() {
		HTTPPluginControl.getConnectionDefaults().timeout = 6000
		test = new GTest(1, "HAR2Script")
		request = new HTTPRequest()
		grinder.logger.info("before process.");
	}

    @BeforeThread
	public void beforeThread() {
		test.record(this, "test")
		grinder.statistics.delayReports=true;
		grinder.logger.info("before thread.");
	}

	@Before
	public void before() {
<#if commonHeaders?? && commonHeaders?size != 0>
		/** Common Headers */
		request.setHeaders(nvs([
	<#assign keys = commonHeaders?keys>
	<#list keys as key>
			"${key}" : "${commonHeaders[key]?replace("$", "\\$")}" <#if key?has_next>,</#if>
	</#list>
		]))
</#if>
		grinder.logger.info("before thread. init headers and cookies");
	}

	@Test
	public void test(){
<#if request??>
	<#list request as request>
		<#if request.perRequestHeaders?? && request.perRequestHeaders?size != 0>

		rquest.setHeaders(nvs([
			<#assign keys = request.perRequestHeaders?keys>
			<#list keys as key>
			"${key}" : "${request.perRequestHeaders[key]?replace("$", "\\$")}" <#if key?has_next>,</#if>
			</#list>
		]))
		</#if>
		<#if request.queryString?? && request.queryString?size != 0>
			<#assign queryKeys = request.queryString?keys>
		<#else>
		public static NVPair[] params = []
		</#if>
		HTTPResponse result = request.${request.method?default("GET")}("${request.url}", <#if request.queryString?? && request.queryString?size != 0>nvs([<#list queryKeys as queryKey>"${queryKey}" : "${request.queryString[queryKey]?replace("$", "\\$")}" <#if queryKey?has_next>,</#if></#list>])<#else>params</#if>)
		assertThat(result.statusCode, is(${request.responseState}));
	</#list>
</#if>

	}

	// Provide a method to convert map to NVPair array
	def nvs(def map) {
		def nvs = []
		map.each {
			key, value ->  nvs.add(new NVPair(key, value))
		}
		return nvs as NVPair[]
	}

}


