/*
 * Copyright 2002-2017 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.phoenixnap.oss.ramlapisync.naming;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.containsOnly;
import static org.apache.commons.lang3.StringUtils.difference;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.splitByCharacterTypeCamelCase;
import static org.apache.commons.lang3.StringUtils.upperCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsonschema2pojo.util.NameHelper;
import org.raml.v2.internal.utils.Inflector;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import com.phoenixnap.oss.ramlapisync.raml.RamlAction;
import com.phoenixnap.oss.ramlapisync.raml.RamlActionType;
import com.phoenixnap.oss.ramlapisync.raml.RamlResource;

/**
 * Class containing methods relating to naming converntions and string cleanup for naming
 * 
 * @author Kurt Paris
 * @since 0.0.1
 *
 */
public class NamingHelper {

	private static final Pattern CLASS_SUFFIXES_TO_CLEAN = Pattern.compile(
			"^(.+)(services|service|impl|class|controller)", Pattern.CASE_INSENSITIVE);
	
	private static final Pattern CONTENT_TYPE_VERSION = Pattern.compile(
			"[^v]*(v[\\d\\.]*).*", Pattern.CASE_INSENSITIVE);

    private static final Pattern SLASH = Pattern.compile("/");

	private static final String ILLEGAL_CHARACTER_REGEX = "[^0-9a-zA-Z_$]";
	
	private static NameHelper cachedNameHelper;
	
	private static NameHelper getNameHelper() {
		if (cachedNameHelper != null) {
			return cachedNameHelper;
		}
		
		cachedNameHelper = new NameHelper(SchemaHelper.getDefaultGenerationConfig());
		return cachedNameHelper;
		
	}
	
	/**
	 * Converts an http contentType into a qualifier that can be used within a Java method
	 * 
	 * @param contentType The content type to convert application/json
	 * @return qualifier, example V1Html
	 */
	public static String convertContentTypeToQualifier (String contentType) {
		//lets start off simple since qualifers are better if they are simple :)
		//if we have simple standard types lets add some heuristics
		if (contentType.equals(MediaType.APPLICATION_JSON_VALUE)) {
			return "AsJson";
		}
		
		if (contentType.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
			return "AsBinary";
		}
		
		if (contentType.equals(MediaType.TEXT_PLAIN_VALUE) || contentType.equals(MediaType.TEXT_HTML_VALUE)) {
			return "AsText";
		}
		
		//we have a non standard type. lets see if we have a version
		Matcher versionMatcher = CONTENT_TYPE_VERSION.matcher(contentType);
		if (versionMatcher.find()) {
			String version = versionMatcher.group(1);
			
			if (version != null) {
				return StringUtils.capitalize(version).replace(".", "_");
			}
		}
		
		//if we got here we have some sort of funky content type. deal with it
		int seperatorIndex = contentType.indexOf("/");
		if (seperatorIndex != -1 && seperatorIndex < contentType.length()) {
			String candidate = contentType.substring(seperatorIndex+1).toLowerCase();
			String out = "";
			if (candidate.contains("json")) {
				candidate = candidate.replace("json", "");
				out += "AsJson";
			}
			
			candidate = StringUtils.deleteAny(candidate, " ,.+=-'\"\\|~`#$%^&\n\t");
			if (StringUtils.hasText(candidate)) {
				out = StringUtils.capitalize(candidate) + out;
			}
			return "_" + out;
		}
		return "";
	}
	
	/**
	 * Checks if a Resource URI fragment is a URI Parameter. URI parameters are defined as {myParameter}
	 * 
	 * @param resource The Resource key/ relative URL
	 * @return If true this URI is a frament containing a URI parameter
	 */
	public static boolean isUriParamResource(String resource) {
		if (resource == null) {
			return false;
		}
		resource = NamingHelper.cleanLeadingAndTrailingNewLineAndChars(resource.toLowerCase());
		if (resource.startsWith("{") && resource.endsWith("}")) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Extracts a list of URI Parameters from a url
	 * 
	 * @param url String to extract parameters from
	 * @return A list of the uri parameters in this URL
	 */
	public static List<String> extractUriParams(String url) {
		List<String> outParams = new ArrayList<>();
		if (StringUtils.hasText(url)) {
			String[] split = StringUtils.split(url, "/");
			for (String part : split) {
				int indexOfStart = part.indexOf("{");
				int indexOfEnd = part.indexOf("}");
				if (indexOfStart != -1 && indexOfEnd != -1 && indexOfStart < indexOfEnd) {
					outParams.add(part.substring(indexOfStart+1, indexOfEnd));
				}
			}
		
		}
		return outParams;
	}

	/**
	 * Utility method to check if a string can be used as a valid class name
	 * 
	 * @param input String to check
	 * @return true if valid
	 */
	public static boolean isValidJavaClassName(String input) {
		if (!StringUtils.hasText(input)) {
			return false;
		}
		if (!Character.isJavaIdentifierStart(input.charAt(0))) {
			return false;
		}
		if (input.length() > 1) {
			for (int i = 1 ; i < input.length(); i++) {
				if (!Character.isJavaIdentifierPart(input.charAt(i))) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Utility method to clean a string for use within javadoc 
	 * 
	 * @param input String to be cleaned
	 * @return The cleaned string
	 */
	public static String cleanForJavadoc(String input) {
		return cleanLeadingAndTrailingNewLineAndChars(input);
	}
	
	/**
	 * Utility method to clean New Line,Spaces and other highly useless characters found (mainly in javadoc)
	 * 
	 * @param input The string to be cleaned
	 * @return Cleaned String
	 */
	public static String cleanLeadingAndTrailingNewLineAndChars(String input) {

		if (!StringUtils.hasText(input)) {
			return input;
		}
		String output = input;
		output = output.replaceAll("[\\s]+\\*[\\s]+", " ");
		while (output.startsWith("/") || output.startsWith("\n") || output.startsWith("*") || output.startsWith("-")
				|| output.startsWith("\t") || output.startsWith(" ") || output.startsWith("\\")) {
			output = output.substring(1);
		}

		while (output.endsWith("/") || output.endsWith("\n") || output.endsWith(" ") || output.endsWith(",")
				|| output.endsWith("\t") || output.endsWith("-") || output.endsWith("*")) {
			output = output.substring(0, output.length() - 1);
		}
		return output;
	}
	
	/**
	 * Convert a name into a java className.
	 *
	 * eg. MonitorServiceImpl becomes Monitor
	 *
	 * @param clazz The name
	 * @return The name for this using Java class convention
	 */
	public static String convertToClassName(String clazz) {		
		return StringUtils.capitalize(cleanNameForJava(clazz));
	}

	/**
	 * Convert a class name into its restful Resource representation.
	 *
	 * eg. MonitorServiceImpl becomes Monitor
	 *
	 * @param clazz The Class to name
	 * @return The name for this class
	 */
	public static String convertClassName(Class<?> clazz) {
		String convertedName = clazz.getSimpleName();
		boolean clean = true;
		do {
			Matcher cleaner = CLASS_SUFFIXES_TO_CLEAN.matcher(convertedName);
			if (cleaner.matches()) {
				if (cleaner.group(1) != null && cleaner.group(1).length() > 0) {
					convertedName = cleaner.group(1);
				}
			} else {
				clean = false;
			}
		} while (clean);
		return StringUtils.uncapitalize(convertedName);
	}
	
	/**
	 * Attempts to load system propertes from the string or use included defaults if available
	 * 
	 * @param inputString Strign containing spring property format
	 * @return resolved String
	 */
	public static String resolveProperties(String inputString) {
		if (!StringUtils.hasText(inputString)) {
			return inputString;
		}
		String tempString = inputString.trim();
		String outString = "";
		int startIndex = 0;
		while (tempString.indexOf("${", startIndex) != -1) {
			int startsWithPos = tempString.indexOf("${", startIndex);
			int endsWithPos = tempString.indexOf("}", startsWithPos+2);
			int nextBracket = tempString.indexOf("{", startsWithPos+2);
			if (nextBracket != -1 && endsWithPos > nextBracket) {
				endsWithPos = tempString.indexOf("}", endsWithPos+1);
			}
			int defaultPos = tempString.lastIndexOf(":", endsWithPos);
			if (defaultPos < startsWithPos) {
				defaultPos = -1;
			}
			
			if (startsWithPos != -1 && endsWithPos != -1) {
				String value = tempString.substring(startsWithPos+2,endsWithPos);
				String defaultString;
				String key;				
				
				if (defaultPos != -1) {
					//lets get default.
					defaultString = value.substring(value.lastIndexOf(":") +1);
					key = value.substring(0, value.lastIndexOf(":"));
				} else {
					key = value;
					defaultString = value;
				}
				
				outString += tempString.substring(startIndex, startsWithPos) + System.getProperty(key, defaultString);
				startIndex = endsWithPos+1;
			}		
		}
		if (startIndex < tempString.length()) {
			outString += tempString.substring(startIndex);
		}
		return outString;
	}

	/**
	 * Attempts to infer the name of a resource from a resources's relative URL
	 * 
	 * @param resource The raml resource being parsed
	 * @param singularize indicates if the resource name should be singularized or not
	 * @return A name representing this resource or null if one cannot be inferred
	 */
	public static String getResourceName(RamlResource resource, boolean singularize) {
		String url = resource.getRelativeUri();
    	if (StringUtils.hasText(url)) {
			if (url.contains("/") 
					&& (url.lastIndexOf("/") < url.length())) {
				return getResourceName(url.substring(url.lastIndexOf("/")+1), singularize);
			}
		}
    	return null;
	}
	
	/**
	 * Attempts to infer the name of a resource from a resources's full URL.
	 * 
	 * @param url The URL of the raml resource being parsed
	 * @param singularize Indicates if the resource name should be singularized or not
	 * @param resourceDepthInClassNames The depth of uri to be included in a name 
	 * @param resourceTopLevelInClassNames The top level of URI to be included in a name
	 * @param reverseOrderInClassNames Is order of URI parts included in a name reversed
	 * @return name of a resource
	 */
	public static String getAllResourcesNames(String url, boolean singularize, int resourceDepthInClassNames, int resourceTopLevelInClassNames, boolean reverseOrderInClassNames) {

		StringBuilder stringBuilder = new StringBuilder();
		if (StringUtils.hasText(url)) {
			String[] resources = SLASH.split(url);
			int lengthCounter = 0;
			for(int i = resources.length - 1; i >= resourceTopLevelInClassNames + 1; --i){
				if (StringUtils.hasText(resources[i])) {
					String resourceName = getResourceName(resources[i], singularize);
					if (reverseOrderInClassNames) {
						stringBuilder.append(resourceName);
					} else {
						stringBuilder.insert(0, resourceName);
					}
					++lengthCounter;
				}
				if(resourceDepthInClassNames > 0 && lengthCounter >= resourceDepthInClassNames){
					break;
				}
			}
		}
		
		return stringBuilder.toString();
	}
	
	/**
	 * Attempts to infer the name of a resource from a resources's relative URL
	 * 
	 * @param resource The Url representation of this object
	 * @param singularize indicates if the resource name should be singularized or not
	 * @return A name representing this resource or null if one cannot be inferred
	 */
	public static String getResourceName(String resource, boolean singularize) {
		if (StringUtils.hasText(resource)) {
				String resourceName = StringUtils.capitalize(resource);
				if (singularize) {
					resourceName = singularize(resourceName);
				} 
				resourceName = StringUtils.capitalize(cleanNameForJava(resourceName));
				return resourceName;
		}
    	
    	return null;
	}
	
	/**
	 * Singularises a string. uses underlying raml parser system
	 * 
	 * @param target name to singularize
	 * @return singularized name
	 */
	public static String singularize(String target) {
		//TODO we should add this as an issue in the RamlBase project and provide a pull request
		String result = Inflector.singularize(target);
		if ((target.endsWith("ss")) && (result.equals(target.substring(0, target.length()-1)))) {
			result = target;
		}
		return result;
	}

	/**
	 * Converts the name of a parameter into a name suitable for a Java parameter
	 *
	 * @param name The name of a RAML query parameter or request header
	 * @return A name suitable for a Java parameter
    */
	public static String getParameterName(String name) {
		return StringUtils.uncapitalize(cleanNameForJava(name));
	}

	/**
	 * Cleans a string with characters that are not valid as a java identifier
	 * 
	 * @param resourceName The string to clean
	 * @return cleaned string
	 */
	public static String cleanNameForJava(String resourceName) {
		String outString = resourceName;
		if (StringUtils.hasText(resourceName)) {
			outString = getNameHelper().normalizeName(resourceName);
			if (StringUtils.hasText(outString)) {
				outString = outString.replaceAll(NameHelper.ILLEGAL_CHARACTER_REGEX, "");
			}
		}
		return outString;
	}

	/**
	 * Cleans a string with characters that are not valid as a java identifier enum
	 * 
	 * @param enumConstant The string to clean
	 * @return cleaned string
	 */
	public static String cleanNameForJavaEnum(String enumConstant) {
		if (!StringUtils.hasText(enumConstant)) {
			return enumConstant;
		}
		
		List<String> nameGroups = new ArrayList<>(asList(splitByCharacterTypeCamelCase(enumConstant)));

        nameGroups.removeIf(s -> containsOnly(s.replaceAll(ILLEGAL_CHARACTER_REGEX, "_"), "_"));

        String enumName = upperCase(join(nameGroups, "_"));
        if (isEmpty(enumName)) {
            enumName = "_DEFAULT_";
        } else if (Character.isDigit(enumName.charAt(0))) {
            enumName = "_" + enumName;
        }

        return enumName;
	}
	

	/**
	 * Attempts to infer the name of an action (intent) from a resource's relative URL and action details
	 * 
	 * @param controllerizedResource The resource that is mapped to the root controller
	 * @param resource The child resource that will be mapped as a method of the root controller
	 * @param actionType The ActionType/HTTP Verb for this Action
	 * @return The java name of the method that will represent this Action
	 */
	public static String getActionName(RamlResource controllerizedResource, RamlResource resource, RamlActionType actionType) {
		
		String url = resource.getUri();
		//Since this will be part of a resource/controller, remove the parent portion of the URL if enough details remain
		//to infer a meaningful method name
		if (controllerizedResource != resource
				&& StringUtils.countOccurrencesOf(url, "{") < StringUtils.countOccurrencesOf(url, "/")-1) {
			url = reduceToResourceNameAndId(url);
		}
		
		//sanity check
    	if (StringUtils.hasText(url)) {
    		
    		//Split the url into segments by seperator
    		String[] splitUrl = SLASH.split(url);
    		String name = "";
    		int numberOfIdsParsed = 0;
    		int index = splitUrl.length-1;
    		boolean singularizeNext = false;
    		boolean isIdInPath = false;

    		//Parse segments until end is reached or we travers a maximum of 2 non Path Variable segments, these 2 should both have at least 1
    		//id path variable each otherwise they would have been typically part of the parent controller
    		//or we have REALLY long URL nesting which isnt really a happy place.
    		while (numberOfIdsParsed < 2 && index >= 0) {
    			
    			String segment = splitUrl[index];
    			//Lets check for ID path variables
    			if (segment.contains("{") && segment.contains("}")) {
    				//should we add this to Method name
					if (index > 0 && index == splitUrl.length-1) {
						//set if the last segment of the url is an Id
						isIdInPath = true;
						if (segment.startsWith("{") && segment.endsWith("}")) {
                            String peek = splitUrl[index-1].toLowerCase();
    					    name = "By" + StringUtils.capitalize(difference(peek, segment.substring(1, segment.length()-1)));
    					} else {
    						String[] split = segment.split("[{}]");
    						name = "By";
    						for(String segmentPart : split) {
    							name = name + StringUtils.capitalize(segmentPart);
    						}
    					}
    				}
    				//Since we probably found an ID, it means that method acts on a single resource in the collection. probably :)
    				singularizeNext = true;
    			} else {
    				segment = cleanNameForJava(segment);
    				if (singularizeNext) { //consume singularisation
    					if (!segment.endsWith("details")) {
    						name = NamingHelper.singularize(StringUtils.capitalize(segment)) + name;
    					} else {
    						name = StringUtils.capitalize(segment) + name;
    					}
        				singularizeNext = false;
        			} else {
        				name = StringUtils.capitalize(segment) + name;
        			}
    				
    				numberOfIdsParsed ++;
				}
    			index--;
    		}
    		
    		//Add the http verb into the mix
    		String tail = splitUrl[splitUrl.length-1];
    		String prefix = convertActionTypeToIntent(actionType, isIdInPath);
    		//singularize name if it's a proper POST or PUT
    		if (!NamingHelper.singularize(tail).equals(tail) && !tail.endsWith("details")
					&& (RamlActionType.POST.equals(actionType) || RamlActionType.PUT.equals(actionType) && isIdInPath)) {
    			name = NamingHelper.singularize(name);
    		}

    		return prefix + name;
		}
    	//Poop happened. return nothing
		return null;
	}

    /**
     * Reduces long URL paths to a format {@code /resource/{id}}"
     * @param url
     * @return
     */
	private static String reduceToResourceNameAndId(String url) {
        String[] splitUrl = SLASH.split(url);
        String slash = "/";
        return slash + splitUrl[splitUrl.length - 2] + slash + splitUrl[splitUrl.length - 1];
	}

	/**
	 * Attempts to convert the Http Verb into a textual representation of Intent based on REST conventions
	 * 
	 * @param actionType The ActionType/Http verb of the action
	 * @param isIdInPath True if the path contains an Id meaning that it must be an idempotent operation, i.e. PUT
	 * @return method name prefix
	 */
	private static String convertActionTypeToIntent(RamlActionType actionType, boolean isIdInPath) {
		switch (actionType) {
			case DELETE : return "delete";
			case GET : return "get";
			case POST : if (!isIdInPath) {
							return "create";
						}
			case PUT:	return "update";
			case PATCH : return "modify";
			default : return "do";
		}
	}
	
	/**
	 * Returns the default sub package that will be used for model objects used in the Request/Response body
	 * @return the package suffix to be appended.
	 */
	public static String getDefaultModelPackage() {
		return ".model";
	}

}
