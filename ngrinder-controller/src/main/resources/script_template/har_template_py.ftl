# -*- coding:utf-8 -*-

# A simple example using the HTTP plugin that shows the retrieval of a
# single page via HTTP.
#
# This script is automatically generated by ngrinder.
#
# @author your_name
from net.grinder.script.Grinder import grinder
from net.grinder.script import Test
from net.grinder.plugin.http import HTTPRequest
from net.grinder.plugin.http import HTTPPluginControl
from java.util import Date
from HTTPClient import NVPair, Cookie, CookieModule

control = HTTPPluginControl.getConnectionDefaults()
# if you don't want that HTTPRequest follows the redirection, please modify the following option 0.
# control.followRedirects = 1
# if you want to increase the timeout, please modify the following option.
control.timeout = 6000

test1 = Test(1, "HAR2Script")
request1 = HTTPRequest()

# Set header datas
headers = [] # Array of NVPair
<#if commonHeaders?? && commonHeaders?size != 0>
	# Common Headers
	<#assign keys = commonHeaders?keys>
	<#list keys as key>
	headers.append(NVPair("${key}", "${commonHeaders[key]?replace("$", "\\$")}"))
	</#list>
</#if>

class TestRunner:
# initlialize a thread
	def __init__(self):
		test1.record(TestRunner.__call__)
		grinder.statistics.delayReports=True
		pass

	def before(self):
		request1.headers = headers

	# test method
	def __call__(self):
		self.before()

	# Choose of Request
<#if request??>
	<#list request as request>
		<#if request.perRequestHeaders?? && request.perRequestHeaders?size != 0>
		<#assign keys = request.perRequestHeaders?keys>
		<#list keys as key>
	headers.append(NVPair("${key}", "${request.perRequestHeaders[key]?replace("$", "\\$")}"))
		</#list>
		</#if>
	public static NVPair[] params = []
		<#if request.queryString?? && request.queryString?size != 0>
			<#assign queryKeys = request.queryString?keys>
			<#list queryKeys as queryKey>
	params.append(NVPair("${queryKey}", "${request.queryString[queryKey]?replace("$", "\\$")}"))
			</#list>
		</#if>
	result = request1.${request.method?default("GET")}("${request.url}", params)

	</#list>
</#if>

	# You get the message body using the getText() method.
	# if result.getText().find("HELLO WORLD") == -1 :
	#	 raise

	# if you want to print out log.. Don't use print keyword. Instead, use following.
	# grinder.logger.info("Hello World")

	if result.getStatusCode() == 200 :
		return
	elif result.getStatusCode() in (301, 302) :
		grinder.logger.warn("Warning. The response may not be correct. The response code was %d." %  result.getStatusCode())
		return
	else :
		raise



