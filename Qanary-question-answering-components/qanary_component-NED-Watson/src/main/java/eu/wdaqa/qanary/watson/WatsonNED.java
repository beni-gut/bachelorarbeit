package eu.wdaqa.qanary.watson;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
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
	 * implement this method encapsulating the functionality of your Qanary
	 * component, some helping notes w.r.t. the typical 3 steps of implementing a
	 * Qanary component are included in the method (you might remove all of them)
	 * 
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

		// STEP 1: get the required data from the Qanary triplestore (the global process memory)

		// if required, then fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<String>(myQanaryMessage);

		// TODO: define the SPARQL query fetch the data that your component requires
		String sparqlSelectQuery = "..."; // define your SPARQL SELECT query here

		ResultSet resultset = myQanaryUtils.selectFromTripleStore(sparqlSelectQuery);
		while (resultset.hasNext()) {
			QuerySolution tupel = resultset.next();
			// TODO: retrieve the data you need to implement your component's functionality
		}

		// STEP 2: compute new knowledge about the given question
		// TODO: implement the custom code for your component

		// STEP 3: store computed knowledge about the given question into the Qanary triplestore 
		// (the global process memory)

		logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
				myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
				myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		// push data to the Qanary triplestore
		// TODO: define your SPARQL UPDATE query here
		String sparqlUpdateQuery = "..."; 

		myQanaryUtils.updateTripleStore(sparqlUpdateQuery, myQanaryMessage.getEndpoint());

		return myQanaryMessage;
	}
}
