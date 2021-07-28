package eu.wdaqua.qanary.diambiguationproperty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


//import com.github.andrewoma.dexx.collection.Map;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import org.apache.http.client.ClientProtocolException;
import org.apache.jena.query.ResultSet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;

@Component
/**
 * This component connected automatically to the Qanary pipeline. The Qanary
 * pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * 
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F"
 *      target="_top">Github wiki howto</a>
 */
public class DiambiguationProperty extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(DiambiguationProperty.class);

	private final String applicationName;
	private final Boolean cacheEnabled;
	private final String cacheFile;

	public DiambiguationProperty(@Value("${spring.application.name}") final String applicationName,
								 @Value("${diambiguation-property.cache.enabled}") final Boolean cacheEnabled,
								 @Value("${diambiguation-property.cache.file}") final String cacheFile) {
		this.applicationName = applicationName;
		this.cacheEnabled = cacheEnabled;
		this.cacheFile = cacheFile;
	}
	
	public static String runCurlGetWithParam(String weburl, String data, String contentType)
			throws ClientProtocolException, IOException {
		String xmlResp = "";

		URL url = new URL(weburl+"data="+URLEncoder.encode(data,"UTF-8"));
    	//URL url = new URL(weburl+"?data="+URLEncoder.encode(data,"UTF-8"));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		
		connection.setRequestMethod("GET");
		
		//connection.setDoOutput(true);
		//connection.setRequestProperty("data", data);
		connection.setRequestProperty("Content-Type", contentType);
				
		/*DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
		wr.writeBytes(data);
		wr.flush();
		wr.close();*/
		
		

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(connection.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		xmlResp = response.toString();
		System.out.println("the curl Get output : "+ xmlResp);
		return xmlResp;
	}
	
	/**
	 * runCurlPOSTWithParam is a function to fetch the response from a CURL command
	 * using POST.
	 */
	public static String runCurlPOSTWithParam(String weburl, String data, String contentType) throws Exception {

		// The String xmlResp is to store the output of the Template generator web
		// service accessed via CURL command

		String xmlResp = "";
		try {
			URL url = new URL(weburl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			connection.setRequestProperty("Content-Type", contentType);

			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			wr.writeBytes(data);
			wr.flush();
			wr.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			xmlResp = response.toString();

			System.out.println("Curl Response: \n" + xmlResp);
			logger.info("Response {}", xmlResp);
		} catch (Exception e) {
		}
		return (xmlResp);

	}

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws SparqlQueryFailed, Exception {
		logger.info("cacheFile location: {}", cacheFile);
//		long startTime = System.currentTimeMillis();
		org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);
		Map<String, Map<String, Double>> allProperties = new HashMap<String, Map<String, Double>>();
		logger.info("process: {}", myQanaryMessage);
		// TODO: implement processing of question

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion(myQanaryMessage);
		String question = myQanaryQuestion.getTextualRepresentation();

		boolean hasCacheResult = false;

		if (cacheEnabled) {
			FileCacheResult cacheResult = readFromCache(question);
			logger.info("cached Result: {}", cacheResult.links);
			hasCacheResult = cacheResult.hasCacheResult;
			allProperties.putAll(cacheResult.links);
			logger.info("allProperties MAP after adding results: {}\n", allProperties);
		}

		
		// STEP1: Retrieve the named graph and the endpoint
		String endpoint = myQanaryMessage.getEndpoint().toASCIIString();
		String namedGraph = myQanaryMessage.getInGraph().toASCIIString();
		logger.info("Graph: {}", namedGraph);
		logger.info("Endpoint: {}", endpoint);
		logger.info("InGraph: {}", namedGraph);

		if (!hasCacheResult) {

			// STEP2: Retrieve information that are needed for the computations
			// Here, we need two parameters as input to be fetched from triplestore-
			// question and language of the question.
			// So first, Retrieve the uri where the question is exposed
//		String sparql = "PREFIX qa:<http://www.wdaqua.eu/qa#> " + "SELECT ?questionuri " + "FROM <" + namedGraph + "> "
//				+ "WHERE {?questionuri a qa:Question}";

//		ResultSet result = myQanaryUtils.selectFromTripleStore(sparql, endpoint);
//		String uriQuestion = result.next().getResource("questionuri").toString();
//		logger.info("Uri of the question: {}", uriQuestion);
			// Retrieve the question itself
//		RestTemplate restTemplate = new RestTemplate();
			// TODO: pay attention to "/raw" maybe change that
//		ResponseEntity<String> responseEntity = restTemplate.getForEntity(uriQuestion + "/raw", String.class);
//		String question = responseEntity.getBody();
//		logger.info("Question: {}", question);

			// the below mentioned SPARQL query to fetch annotation of language from
			// triplestore
		/*
		String questionlang = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
				+ "SELECT ?lang " + "FROM <" + namedGraph + "> " //
				+ "WHERE {?q a qa:Question ." //
				+ " ?anno <http://www.w3.org/ns/openannotation/core/hasTarget> ?q ." //
				+ " ?anno <http://www.w3.org/ns/openannotation/core/hasBody> ?lang ." //
				+ " ?anno a qa:AnnotationOfQuestionLanguage  }";
		// Now fetch the language, in our case it is "en".
		ResultSet result1 = myQanaryUtils.selectFromTripleStore(questionlang, endpoint);
		 */
			String language1 = "en";
//		logger.info("Language of the Question: {}", language1);

			String url = "";
			String data = "";
			String contentType = "application/json";

			// http://repository.okbqa.org/components/21 is the template generator URL
			// Sample input for this is mentioned below.
			/*
			 * { "string": "Which river flows through Seoul?", "language": "en" }
			 * http://ws.okbqa.org:1515/templategeneration/rocknrole
			 */

			// now arrange the Web service and input parameters in the way, which is needed
			// for CURL command
			url = "http://ws.okbqa.org:1515/templategeneration/rocknrole"; // @TODO: move to application.properties
			data = "{  \"string\":\"" + question + "\",\"language\":\"" + language1 + "\"}";// "{ \"string\": \"Which river
			// flows through Seoul?\",
			// \"language\": \"en\"}";
			logger.info("Component: 21; data: {}", data);
			String output1 = "";
			// pass the input in CURL command and call the function.

			try {
				output1 = DiambiguationProperty.runCurlPOSTWithParam(url, data, contentType);
			} catch (Exception e) {
			}
			logger.info("The output template is: {}", output1);

			/*
			 * once output is received, now the task is to parse the generated template, and
			 * store the needed information which is Resource, Property, Resource Literal,
			 * and Class. Then push this information back to triplestore. The below code
			 * before step 4 does parse the template. For parsing PropertyRetrival.java file
			 * is used, which is in the same package.
			 *
			 * for this, we create a new object property of Property class.
			 *
			 */

			url = "http://ws.okbqa.org:2357/agdistis/run?"; // @TODO: move to application.properties
			data = output1.substring(1, output1.length() - 1);
			contentType = "application/json";

			logger.info("Component: 7; data: {}", data);
			output1 = "";
			try {
				output1 = DiambiguationProperty.runCurlGetWithParam(url, data, contentType);
			} catch (Exception e) {
			}
			logger.info("The output template is: {}", output1);

			JSONParser parser = new JSONParser();

			try {

				JSONObject json = (JSONObject) parser.parse(output1);

				JSONArray characters = (JSONArray) json.get("ned");
				Iterator i = characters.iterator();

				while (i.hasNext()) {
					JSONObject mainObject = (JSONObject) i.next();
					JSONArray types = (JSONArray) mainObject.get("properties");
					Iterator iTypes = types.iterator();
					String var = "";

					while (iTypes.hasNext()) {
						Map<String, Double> urlsAndScore = new HashMap<String, Double>();
						JSONObject tempObject = (JSONObject) iTypes.next();
						String urls = (String) tempObject.get("value");
						double score = (double) tempObject.get("score");
						var = (String) tempObject.get("var");
						if (allProperties.size() > 0 && allProperties.containsKey(var)) {

							double tScore = (double) allProperties.get(var).entrySet().iterator().next().getValue();
							if (tScore < score) {

								allProperties.get(var).remove(allProperties.get(var).entrySet().iterator().next().getKey());
								allProperties.get(var).put(urls, score);
								logger.info("var: {}, urls: {}, score: {}", var, urls, score);
							}

						} else {
							urlsAndScore.put(urls, score);
							logger.info("var: {}, urls: {}, score: {}", var, urls, score);
							// allUrls.put("classes", urlsAndScore);
							allProperties.put(var, urlsAndScore);
						}

					}
				}
				if (cacheEnabled) {
					this.writeToCache(question, allProperties);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		for (String vars : allProperties.keySet()) {
			Map<String, Double> allUrls = allProperties.get(vars);
			int count = 0;
			for (String urls : allUrls.keySet()) {
				logger.info("Inside : Literal: {}", urls);
				String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
						+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
						+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "  //
						+ "INSERT { " + "GRAPH <" + namedGraph+ "> { " //
						+ " ?a a qa:AnnotationOfRelation . " + " ?a oa:hasTarget [ " + " a oa:SpecificResource; " // 
						+ " oa:hasSource <" + myQanaryQuestion.getUri() + ">; " + " ] . " //
						+ " ?a oa:hasBody <" + urls + "> ;" // 
						+ " oa:annotatedBy <urn:qanary:"+this.applicationName+"> ; " //
						+ " oa:annotatedAt ?time "
						+ "}} " //
						+ "WHERE { "  //
						+ "BIND (IRI(str(RAND())) AS ?a) ." //
						+ "BIND (now() as ?time) " //
						+ "}";
				logger.info("Sparql query: {}", sparql);
				myQanaryUtils.updateTripleStore(sparql, endpoint);
				count++;
			}
			logger.info("Count is: {}",count);
		}

		return myQanaryMessage;
	}


	private FileCacheResult readFromCache(String myQuestion) throws IOException {
		final FileCacheResult cacheResult = new FileCacheResult();
		try {
			File f = ResourceUtils.getFile(cacheFile);
			FileReader fr = new FileReader(f);
			BufferedReader br = new BufferedReader(fr);
			String line;

			while ((line = br.readLine()) != null && !cacheResult.hasCacheResult) {
				String question = line.substring(0, line.indexOf("Answer:"));
				logger.info("Line read: {}", line);
				//logger.info("{}", myQuestion);

				if (question.trim().equals(myQuestion)) {
					String Answer = line.substring(line.indexOf("Answer:") + "Answer:".length());
					logger.info("Question found here {}", Answer);
					Answer = Answer.trim();
					Answer = Answer.substring(1, Answer.length() - 1);
					String[] values = Answer.split(", ");
					logger.info("found values: {}", values);
					for (String value : values) {
						value = value.replace("{", "");
						value = value.replace("}", "");
						String[] subValue = value.split("=");
						String key = subValue[0];
						String uri = subValue[1];
						double score = Double.parseDouble(subValue[2]);

						Map<String, Double> uriAndScore = new HashMap<String, Double>();
						uriAndScore.put(uri, score);
						cacheResult.links.put(key, uriAndScore);
					}
					cacheResult.hasCacheResult = true;
					break;
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			//handle this
			logger.info("{}", e);
		}
		return cacheResult;
	}

	private void writeToCache(String myQuestion, Map<String, Map<String, Double>> links) throws IOException {
		logger.info("trying to write to cache: {}", myQuestion);
		try {
			BufferedWriter buffWriter = new BufferedWriter(new FileWriter(cacheFile, true));
			String MainString = myQuestion + " Answer: " + links.toString();
			buffWriter.append(MainString);
			buffWriter.newLine();
			buffWriter.close();
			logger.info("finished writing to cache");
		} catch (FileNotFoundException e) {
			//handle this
			logger.info("{}", e);
		}
	}

	class FileCacheResult {
		Map<String, Map<String, Double>> links = new HashMap<String, Map<String, Double>>();
		public boolean hasCacheResult;
	}

}