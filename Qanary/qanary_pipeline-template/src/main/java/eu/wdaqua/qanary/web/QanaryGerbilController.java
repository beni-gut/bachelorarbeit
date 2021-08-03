package eu.wdaqua.qanary.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import eu.wdaqua.qanary.commons.QanaryUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
//import org.json.parser.JSONParser;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.QanaryComponentRegistrationChangeNotifier;
import eu.wdaqua.qanary.business.*;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;


/**
 * controller for processing questions, i.e., related to the question answering process
 *
 * @author Dennis Diefenbach
 */
@Controller
public class QanaryGerbilController {
	
    private static final Logger logger = LoggerFactory.getLogger(QanaryGerbilController.class);
    private final QanaryConfigurator qanaryConfigurator;
	private final QanaryComponentRegistrationChangeNotifier qanaryComponentRegistrationChangeNotifier;
 
     private String host;
     private int port;
     
 
    //Set this to allow browser requests from other websites
    @ModelAttribute
    public void setVaryResponseHeader(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
    }

    /**
     * inject QanaryConfigurator
     */
    @Autowired
    public QanaryGerbilController(final QanaryConfigurator qanaryConfigurator,
    							  final QanaryPipelineConfiguration qanaryPipelineConfiguration, 
                                  final QanaryQuestionController qanaryQuestionController,
                                  final QanaryComponentRegistrationChangeNotifier qanaryComponentRegistrationChangeNotifier) {
        this.qanaryConfigurator = qanaryConfigurator;
        this.qanaryComponentRegistrationChangeNotifier = qanaryComponentRegistrationChangeNotifier;
        this.host = qanaryPipelineConfiguration.getHost();
        this.port = qanaryPipelineConfiguration.getPort();
    }

    /**
     * expose the model with the component names
     */
    @ModelAttribute("componentList")
    public List<String> componentList() {
    	List<String> components = qanaryComponentRegistrationChangeNotifier.getAvailableComponentNames();
        logger.info("available components: {}", components);
        return components;
    }

    /**
     * a simple HTML input to generate a url-endpoint for gerbil for QA, http://gerbil-qa.aksw.org/gerbil/config
     */
    @RequestMapping(value = "/gerbil", method = RequestMethod.GET)
    public String startquestionansweringwithtextquestion(Model model) {
        model.addAttribute("url", "Select components!");
        return "generategerbilendpoint";
    }

    /**
     * given a list of components a url-endpoint for gerbil for QA is generated
     *
     */
    @RequestMapping(value = "/gerbil", method = RequestMethod.POST)
    public String gerbilGenerator(
            @RequestParam(value = QanaryStandardWebParameters.COMPONENTLIST, defaultValue = "") final List<String> componentsToBeCalled,
            Model model
    ) throws Exception {
        String urlStr = "";
        if (componentsToBeCalled.size()==0){
            urlStr = "Select components!";
            model.addAttribute("url", urlStr);
        } else {
            //Generate a string like this "wdaqua-core0, QueryExecuter"
            String components = "/gerbil-execute/";
            for (String component : componentsToBeCalled) {
                components += component + ", ";
            }
            logger.info("components (0): {}",components);
            if (components.length() > 0) {
                components = components.substring(0, components.length() - 2);
            }
            logger.info("compoents (1): {}",components);
            //urlStr += URLEncoder.encode(components, "UTF-8")+"/";
            URI uri = new URI(
                    "http",
                    null,
                    new URL(host).getHost(),
                    port,
                    components+"/",
                    null,
                    null);
            URL url = uri.toURL();
            logger.info("created URL: {}", url.toString());
            model.addAttribute("url", url.toString());
        }
        return "generategerbilendpoint";
    }

    @SuppressWarnings("unchecked")
	@RequestMapping(value="/gerbil-execute/{components:.*}",  method = RequestMethod.POST, produces = "application/json")
	public ResponseEntity<?> gerbil(
			@RequestParam(value = "query", required = true) final String query,
            @PathVariable("components") final String componentsToBeCalled
    ) throws Exception, URISyntaxException, SparqlQueryFailed {
    	logger.info("Asked question: {}", query);
        logger.info("QA pipeline components: {}", componentsToBeCalled);
    	MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
        map.add("question", query);
        map.add("componentlist[]", componentsToBeCalled);
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.postForObject(qanaryConfigurator.getHost()+":"+qanaryConfigurator.getPort()+"/startquestionansweringwithtextquestion", map, String.class);
        org.json.JSONObject json = new org.json.JSONObject(response);
        //retrieve text representation, SPARQL and JSON result
        QanaryMessage myQanaryMessage = new QanaryMessage(new URI((String)json.get("endpoint")), new URI((String)json.get("inGraph")), new URI((String)json.get("outGraph")));
        @SuppressWarnings("rawtypes")
		QanaryQuestion<?> myQanaryQuestion = new QanaryQuestion(myQanaryMessage);
        //Generates the following output
    	/*{
 		   "questions":[
 		      "question":{
 		         "answers":"...",
 		         "language":[
 		            {
 		               "SPARQL":"..."
 		            }
 		         ]
 		      }
 		   ]
 		}*/


        /**
         * Changed source
         *
         * Needs to be in the form of:
         * {
         * 	"questions": [{
         * 		"id": "1",
         * 		"question": [{
         * 			"language": "en",
         * 			"string": "Which German cities have more than 250000 inhabitants?"
         *                }],
         * 		"query": {
         * 			"sparql": "SELECT DISTINCT ?uri WHERE { { ?uri <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/City> . } UNION { ?uri <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Town> . }  ?uri <http://dbpedia.org/ontology/country> <http://dbpedia.org/resource/Germany> .  ?uri <http://dbpedia.org/ontology/populationTotal> ?population .  FILTER ( ?population > 250000 ) } "
         *        },
         * 		"answers": [{
         * 			"head": {
         * 				"vars": [
         * 					"uri"
         * 				]
         *            },
         * 			"results": {
         * 				"bindings": [{
         * 					"uri": {
         * 						"type": "uri",
         * 						"value": "http://dbpedia.org/resource/Bonn"
         *                    }
         *                }]
         *            }
         *        }]* 	}]
         * }
         *
         *
         * */
        //contained in wrapper json "obj"
        //asked question json object
        JSONArray questionDataArray = new JSONArray();
        JSONObject questionData = new JSONObject();
        //asked question json object, data of question
        questionData.put("language", "en"); //TEST
        String questionText = myQanaryQuestion.getTextualRepresentation();
        questionData.put("string", questionText);
        //add question Data to wrapper obj
        questionDataArray.add(questionData);

        //sparql query object
        JSONObject sparqlQuery = new JSONObject();
        sparqlQuery.put("sparql", myQanaryQuestion.getSparqlResult());

        //answers, contains "head" and "results"
        JSONArray answersArray = new JSONArray();
        JSONObject answersObj = new JSONObject();

//        //in answers, head contains vars, defines which type of variable will be returned
//        JSONObject head = new JSONObject();
//        JSONArray vars = new JSONArray();
//        //variable data
//        vars.add("uri"); //TEST
//        //add objects to wrappers
//        head.put("vars", vars);
//
//        //results contains the bindings of the results
//        JSONObject results = new JSONObject();
//        JSONArray bindingsArray = new JSONArray();
//        JSONObject dataObj = new JSONObject();
//        JSONObject data = new JSONObject();
//        //answer data
//        data.put("type", "uri"); //TEST
//        data.put("value", "the correct dbpedia uri"); //TEST
//        //add to wrappers
//        dataObj.put("uri", data);
//        bindingsArray.add(dataObj);
//        results.put("bindings", bindingsArray);
//
//        //add everything into answers array
//        answersObj.put("head", head);
//        answersObj.put("results", results);

        //JsonResult is what's contained in the "answers" Array
        //so "head" and "results" as next-level Objects/Arrays
        String qanaryJsonAnswerString = (String) myQanaryQuestion.getJsonResult();
        logger.info("Line 249, JSON Answer String, {}", qanaryJsonAnswerString);

        boolean lengthTest = true;

        //prevents parser from failing with ParseException due to String returned being of length zero
        if (qanaryJsonAnswerString.length() > 0) {
            //Parse the retrieved String to a JSON Object
            JSONParser parser = new JSONParser();
            answersObj = (JSONObject) parser.parse(qanaryJsonAnswerString);

            logger.info("answers returned to GerbilController: \n{} \n\n", answersObj);

            //is true if String is returned but it only contains: {"bindings":[]} meaning it will be true if there is no answer
            lengthTest = (answersObj.get("results").toString().length() <= 15);
            logger.info("length Test: {}", lengthTest);
        }

        //if answers are not empty (length bigger than 15), add the Object to the Array
        if (!lengthTest) {
            answersArray.add(answersObj);
        }


        //wrapper json
        JSONObject questionsObj = new JSONObject();
        JSONArray questionsArray = new JSONArray();
        JSONObject questionsTtem = new JSONObject();

        //put everything into wrapper object
        //id?
//        questionsTtem.put("id", "1"); //TEST
        questionsTtem.put("question", questionDataArray);
        questionsTtem.put("query", sparqlQuery);
        questionsTtem.put("answers", answersArray);
        questionsArray.add(questionsTtem);

        questionsObj.put("questions", questionsArray);

        //isn't properly returned yet?
        logger.info("Returned JSON object: {}", questionsObj);

        return new ResponseEntity<JSONObject>(questionsObj,HttpStatus.OK);


        //        JSONObject obj = new JSONObject();
//        JSONArray questions = new JSONArray();
//        JSONObject item = new JSONObject();
//        JSONArray question = new JSONArray();
//        JSONObject queryJson = new JSONObject();
//
//        JSONArray answers = new JSONArray();
//        JSONArray answersArray = new JSONArray();
//
//        JSONObject qanaryAnno = new JSONObject();
//        JSONParser parser = new JSONParser();
//
//        /**
//         * probably an "id" is needed in "questions", example:
//         *
//         * item.put("id", "1")
//         *
//         * */
//
//        try {
//            /**
//             * needs two children
//             * 1. "head", contains "vars": ["uri"]
//             * 2. "results", contains "bindings": [{ "uri": {"type": "uri", "value": "uri-to-dbpedia"}}]
//             *
//             * */
//            JSONObject answerContent = (JSONObject) parser.parse(myQanaryQuestion.getJsonResult());
//            if(!(answerContent.isEmpty())){
//                //if empty, dont push into answers array, for gerbil is easily confused
//                answersArray.add(answerContent);
//            }
//            item.put("answers", answersArray);
//        } catch (Exception e) {
//            item.put("answers", answers);
//        }
//
//        JSONObject temp = new JSONObject();
//        temp.put("language", "en");
//        /**
//         * probably also needs "string": "String of asked Question" in "question"
//         *
//         * */
//
//        question.add(temp);
//
//        getQueries changed in "qanary_commons/main/.../commons/QanaryQuestion.java" to "getSparqlResult"
//
//        item.put("query", myQanaryQuestion.getSparqlResult());
//        item.put("question", question);
//        questions.add(item);
//        obj.put("questions", questions);
//        qanaryAnno.put("entities", myQanaryQuestion.getEntities());
//        qanaryAnno.put("properties", myQanaryQuestion.getProperties());
//        qanaryAnno.put("classes", myQanaryQuestion.getClasses());
//        item.put("qanaryAnno",qanaryAnno);
    }
}