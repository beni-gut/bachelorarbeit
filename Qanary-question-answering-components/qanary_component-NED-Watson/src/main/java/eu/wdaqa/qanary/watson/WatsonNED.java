package eu.wdaqa.qanary.watson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.net.URI;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import org.json.JSONArray;
import org.json.JSONObject;
import com.google.gson.Gson;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


@Component
/**
 * This component retrieves named entities for a given question from the
 * IBM Watson Natural Language Understanding Web Service
 */
public class WatsonNED extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(WatsonNED.class);

	private final String applicationName;
	private final boolean cacheEnabled;
	private final String cacheFile;
	private final URI watsonServiceURL;
	private final String watsonServiceKey;

	public WatsonNED(
			@Value("${spring.application.name}")final String applicationName,
			@Value("${ned-watson.cache.enabled}") final boolean cacheEnabled,
			@Value("${ned-watson.cache.file}") final String cacheFile,
			@Value("${ned-watson.service.url}") final URI watsonServiceURL,
			@Value("${ned-watson.service.key}") final String watsonServiceKey
	) {
		this.applicationName = applicationName;
		this.cacheEnabled = cacheEnabled;
		this.cacheFile = cacheFile;
		this.watsonServiceURL = watsonServiceURL;
		this.watsonServiceKey = watsonServiceKey;
	}

	/**
	 * method encapsulating the functionality of the Qanary component
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<>(myQanaryMessage);

		// textual representation/String of question
		String myQuestionText = myQanaryQuestion.getTextualRepresentation();

		// variable setup
		List<NamedEntity> namedEntityList = new ArrayList<>();
		boolean hasCacheResult = false;

		// if cache is enabled in application.properties, try to find the question in the text file
		// add all found entities to the List
		if (cacheEnabled) {
			CacheResult cacheResult = readFromCache(myQuestionText);
			hasCacheResult = cacheResult.hasCacheResult;
			namedEntityList.addAll(cacheResult.dataWatson);
		}

		// if no cacheResult was found, or if cache is turned off, get data from Watson service and add it to the List
		if (!hasCacheResult) {
			namedEntityList = this.retrieveDataFromWebService(myQuestionText);
		}

		for (NamedEntity namedEntity : namedEntityList) {
			// the sparql query to push all entities into the triplestore
			String sparqlUpdateQuery = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/>  " //
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>" //
					+ "INSERT { " //
					+ "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
					+ "?a a qa:AnnotationOfInstance . " //
					+ "?a oa:hasTarget [ " //
					+ "a    oa:SpecificResource; " //
					+ "oa:hasSource    <" + myQanaryQuestion.getUri() + ">; \n" //
					+ "oa:hasSelector  [ " //
					+ "a oa:TextPositionSelector ; " //
					+ "oa:start \"" + namedEntity.getStart() + "\"^^xsd:nonNegativeInteger ; " //
					+ "oa:end  \"" + namedEntity.getEnd() + "\"^^xsd:nonNegativeInteger ; " //
					+ "qa:score \"" + namedEntity.getConfidence() + "\"^^xsd:float " //
					+ "] " //
					+ "] . " //
					+ "?a oa:hasBody <" + namedEntity.getUri() + "> ; \n" //
					+ "oa:annotatedBy <urn:qanary:" + this.applicationName + "> ; " //
					+ "oa:annotatedAt ?time  " + "}} " //
					+ "WHERE { " //
					+ "BIND (IRI(str(RAND())) AS ?a) . " //
					+ "BIND (now() as ?time) " //
					+ "} ";

			myQanaryUtils.updateTripleStore(sparqlUpdateQuery, myQanaryMessage.getEndpoint().toString());
		}
		return myQanaryMessage;
	}

	/**
	 * Requests Data from the Watson WebService
	 * @param myQuestionText The question as String
	 * @return a List with all found Named Entities, which will be further processed in "process"
	 * @throws IOException
	 */
	private List<NamedEntity> retrieveDataFromWebService(String myQuestionText) throws IOException {
		logger.info("Retrieving data from Webservice for Question: {}", myQuestionText);
		ArrayList<NamedEntity> namedEntityArrayList = new ArrayList<>();

		/**
		 * the request body as a String
		 * features defines what the API returns
		 * entities returns the entities with a dbpedia-link if it can find one
		 * 'mentions' so the location of the entity is returned
		 * standard limit is 50
		 */

		// Probably encode as JSONArray/Object and possibly encode QuestionText
		String requestBody = "{\"language\": \"en\","
				+ "\"text\": \"" + myQuestionText
				+ "\",\"features\": {"
				+ "\"entities\": {\"limit\": 5, \"mentions\": true}}}";

		// transforms the request body for the Http request
		StringEntity requestEntity = new StringEntity(requestBody);

		// encodes the API key for Authorization
		String encodedKey = Base64.getEncoder().encodeToString(("apikey:" + watsonServiceKey).getBytes());

		// instances the httpRequest and sets the headers and body
		/**
		 * Check Headers and correct them...
		 */
		HttpClient httpClient = HttpClients.createDefault();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType("application/json");
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey);
		headers.set("User-Agent", "Qanary/" + this.getClass().getName() );
		HttpPost httpRequest = new HttpPost(watsonServiceURL + "/v1/analyze?version=2021-08-01");
//		httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey);
//		httpRequest.setHeader("Content-Type", "application/json");
		httpRequest.setHeader(headers);
		httpRequest.setEntity(requestEntity);

		// executes the http request
		HttpResponse response = httpClient.execute(httpRequest);

		try {
			HttpEntity httpEntity = response.getEntity();
			if (httpEntity != null) {
				InputStream inputStream = httpEntity.getContent();

				String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
				JSONObject responseJsonObject = new JSONObject(text);
				logger.info("received Json Response: \n{}\n", responseJsonObject);
				// test if response returned the entities Array
				if (responseJsonObject.has("entities")) {
					// get the entity Array
					JSONArray responseEntitiesArray = (JSONArray) responseJsonObject.get("entities");
					// test if Array holds entities
					if (responseEntitiesArray.length() != 0) {
						// process each returned entity in the Array
						for (int i = 0; i < responseEntitiesArray.length(); i++) {
							// get the entity and log it
							JSONObject responseEntity = responseEntitiesArray.getJSONObject(i);
							logger.info("responseEntity: {}", responseEntity);

							// check if entity has a disambiguation Array that contains the dbpedia URI
							if (responseEntity.has("disambiguation")) {
								// get location in the question of the entity
								JSONArray locationsArray = (JSONArray) responseEntity.getJSONArray("mentions").getJSONObject(0).get("location");
								int start = locationsArray.getInt(0);
								int end = locationsArray.getInt(1) - 1;

								// get the confidence assigned by Watson
								double confidence = (double) responseEntity.get("confidence");

								// get the disambiguated dbpedia uri
								String uri = (String) responseEntity.getJSONObject("disambiguation").get("dbpedia_resource");
								logger.info("dbpedia_resource: {}, start: {}, end: {}, confidence: {}", uri, start, end, confidence);

								// create new NamedEntity with all Data and add it to the ArrayList
								NamedEntity foundNamedEntity = new NamedEntity(uri, start, end, confidence);
								namedEntityArrayList.add(foundNamedEntity);
							}
						}
					}
				}
			}
			if (cacheEnabled) {
				this.writeToCache(myQuestionText, namedEntityArrayList);
			}
		} catch (ClientProtocolException e) {
			// handle this
			logger.error("ClientProtocolException: {}", e);
		}
		return namedEntityArrayList;
	}

	/**
	 * Searches a text file and tries to find the asked question
	 * if the question is in the text file, return the answer and add it to the Array in CacheResult
	 * @param myQuestionText The String of the asked question
	 * @return the CacheResult with all found answers
	 * @throws IOException
	 */
	private CacheResult readFromCache(String myQuestionText) throws IOException {
		final CacheResult cacheResult = new CacheResult();
		try {
			File f = new File(this.cacheFile);
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line;

			while ((line = br.readLine()) != null) {
				String question = line.substring(0, line.indexOf("Answer:"));
				//logger.info("readLine: {}", line);

				if (question.trim().equals(myQuestionText)) {
					String answer = line.substring(line.indexOf("Answer:") + "Answer:".length());
					logger.info("Here: {}", answer);
					answer = answer.trim();
					JSONArray jsonArr = new JSONArray(answer);
					if (jsonArr.length() != 0) {
						for (int i = 0; i < jsonArr.length(); i ++) {
							JSONObject explrObject = jsonArr.getJSONObject(i);
							NamedEntity namedEntity = new NamedEntity((String) explrObject.get("uri"), (int) explrObject.get("start"), (int) explrObject.get("end"), (double) explrObject.get("confidence"));
							cacheResult.dataWatson.add(namedEntity);
						}
					}
					cacheResult.hasCacheResult = true;
					break;
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			// handle this
			logger.error("File not found: \n{}", e);
		}
		return cacheResult;
	}

	/**
	 * Used to write questions and their answers into a text file as a simple cache
	 * @param myQuestionText The String of the asked question
	 * @param uriAndLocation The Array of all named entities
	 * @throws IOException
	 */
	private void writeToCache(String myQuestionText, ArrayList<NamedEntity> uriAndLocation) throws IOException {
		try {
			// true in FileWriter constructor, so that everything is appended at the end of the document
			BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(cacheFile, true));
			Gson gson = new Gson();

			String json = gson.toJson(uriAndLocation);
			logger.info("gsonwala: {}", json);

			String mainString = myQuestionText + " Answer: " + json;
			bufferedWriter.append(mainString);
			bufferedWriter.newLine();
			bufferedWriter.close();
		} catch (FileNotFoundException e) {
			// handle this
			logger.error("File not found: \n{}", e);
		}
	}

	/**
	 * Class used to return CacheResult
	 */
	class CacheResult {
		public ArrayList<NamedEntity> dataWatson = new ArrayList<>();
		public boolean hasCacheResult;
	}
}
