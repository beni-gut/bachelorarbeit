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

//import org.apache.catalina.util.URLEncoder;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import com.google.gson.Gson;

//import com.github.andrewoma.dexx.collection.ArrayList;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
//import org.apache.tomcat.jni.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;


@Component
/**
 * This component retrieves named entities for a given question from the
 * IBM Watson Natural Language Understanding Web Service
 */
public class WatsonNED extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(WatsonNED.class);

	private final String applicationName;
	private final Boolean cacheEnabled;
	private final String cacheFile;
	private final String watsonServiceURL;
	private final String watsonServiceKey;

	public WatsonNED(
			@Value("${spring.application.name}")final String applicationName,
			@Value("${ned-watson.cache.enabled}") final Boolean cacheEnabled,
			@Value("${ned-watson.cache.file}") final String cacheFile,
			@Value("${ned-watson.service.url}") final String watsonServiceURL,
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
	 * 
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

		// if no cacheResult was found, or if cache is turned off, get data from Watson service
		if (!hasCacheResult) {
			namedEntityList = this.retrieveDataFromWebService(myQuestionText);
		}

		for (NamedEntity namedEntity : namedEntityList) {
			// STEP 3: store computed knowledge about the given question into the Qanary triplestore
			// (the global process memory)

			/*
			logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
					myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
					myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
			*/
			// push data to the Qanary triplestore
			// TODO: define your SPARQL UPDATE query here
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

	private List<NamedEntity> retrieveDataFromWebService(String myQuestionText) throws IOException {
		logger.info("Retrieving data from Webservice for Question: {}", myQuestionText);
		ArrayList<NamedEntity> namedEntityArrayList = new ArrayList<>();

		String encodedQuestion = "";
		encodedQuestion = URLEncoder.encode(myQuestionText, "UTF-8");

		HttpClient httpClient = HttpClients.createDefault();



		HttpPost httpRequest = new HttpPost(watsonServiceURL + "/v1/analyze?version=2021-08-01");
		httpRequest.setHeader("Authorization", watsonServiceKey);
		httpRequest.setHeader("Content-Type", "application/json");


		logger.info("headers: {}", httpRequest.getAllHeaders());

		String requestBody = "{\n" +
				"     \"language\": \"en\",\n" +
				"     \"text\": \""+ encodedQuestion +"\",\n" +
				"     \"features\": {\n" +
				"       \"entities\": {\n" +
				"         \"limit\": 5\n" +
				"       },\n" +
				"       \"concepts\": {\n" +
				"         \"limit\": 5\n" +
				"       }\n" +
				"     }\n" +
				"}";
		StringEntity requestEntity = new StringEntity(requestBody);

		httpRequest.setEntity(requestEntity);
		HttpResponse response = httpClient.execute(httpRequest);

		try {
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream inputStream = entity.getContent();

				String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
				JSONObject responseJsonObject = new JSONObject(text);
				logger.info("responseJSON: {}", responseJsonObject);
			}
		} catch (ClientProtocolException e) {
			logger.error("ClientProtocolException: {}", e);
		}

		return namedEntityArrayList;
	}

	private CacheResult readFromCache(String myQuestionText) throws IOException {
		final CacheResult cacheResult = new CacheResult();
		try {
			File f = new File(this.cacheFile);
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line;

			while ((line = br.readLine()) != null) {
				String question = line.substring(0, line.indexOf("Answer:"));
				logger.info("readLine: {}", line);

				if (question.trim().equals(myQuestionText)) {
					String answer = line.substring(line.indexOf("Answer:") + "Answer:".length());
					logger.info("Here: {}", answer);
					answer = answer.trim();
					JSONArray jsonArr = new JSONArray(answer);
					if (jsonArr.length() != 0) {
						for (int i = 0; i < jsonArr.length(); i ++) {
							JSONObject explrObject = jsonArr.getJSONObject(i);
							NamedEntity namedEntity = new NamedEntity((String) explrObject.get("uri"), (int[]) explrObject.get("location"));
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

	private void writeToCache(String myQuestionText, ArrayList<NamedEntity> uriAndLocation) throws IOException {
		try {
			//FileWriter will append at the end of the document
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

	class CacheResult {
		public ArrayList<NamedEntity> dataWatson = new ArrayList<>();
		public boolean hasCacheResult;
	}
}
