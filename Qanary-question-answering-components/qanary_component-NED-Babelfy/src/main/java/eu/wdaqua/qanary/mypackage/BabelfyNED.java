package eu.wdaqua.qanary.mypackage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class BabelfyNED extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(BabelfyNED.class);

	private final String applicationName;

	public BabelfyNED(@Value("${spring.application.name}") final String applicationName) {
		this.applicationName = applicationName;
	}

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 * @throws Exception 
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);
		// TODO: implement processing of question
		
		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
	    QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion(myQanaryMessage);
	    String myQuestion = myQanaryQuestion.getTextualRepresentation();
	    
	    ArrayList<Link> links = new ArrayList<Link>();
	    try {
			File f = new File("/home/andokai/bachelorarbeit/Qanary-question-answering-components/qanary_component-NED-Babelfy/src/main/resources/questions.txt");
	    	FileReader fr = new FileReader(f);
	    	BufferedReader br  = new BufferedReader(fr);
			int flag = 0;
			String line;
//			Object obj = parser.parse(new FileReader("DandelionNER.json"));
//			JSONObject jsonObject = (JSONObject) obj;
//			Iterator<?> keys = jsonObject.keys();
			
			while((line = br.readLine()) != null && flag == 0) {
			    String question = line.substring(0, line.indexOf("Answer:"));
				logger.info("{}", line);
				logger.info("{}", myQuestion);
				
			    if(question.trim().equals(myQuestion))
			    {
			    	String Answer = line.substring(line.indexOf("Answer:")+"Answer:".length());
			    	logger.info("Here {}", Answer);
			    	Answer = Answer.trim();
			    	JSONArray jsonArr =new JSONArray(Answer);
			    	if(jsonArr.length()!=0)
	 	        	   {
	 	        		   for (int i = 0; i < jsonArr.length(); i++) 
	 	        		   {
	 	        			   JSONObject explrObject = jsonArr.getJSONObject(i);
	 	        			  
	 	        			   logger.info("Question: {}", explrObject);
	 	        			   
	 	        			  Link l = new Link();
		    	                l.begin = (int) explrObject.get("begin");
		    	                l.end = (int) explrObject.get("end")+1;
		    	                l.link= explrObject.getString("link");
		    	                links.add(l);
		            		}
		            	}
			    	flag=1;
			    	
			    	break;	
			    }
			   
			    
			}
			br.close();
			if(flag==0)
			{

	    logger.info("Question {}", myQuestion);
	    String thePath = "";
	    thePath = URLEncoder.encode(myQuestion, "UTF-8"); 
	    logger.info("Path {}", thePath);

	    HttpClient httpclient = HttpClients.createDefault();
	    HttpGet httpget = new HttpGet("https://babelfy.io/v1/disambiguate?text="+thePath+"&lang=AGNOSTIC&key=38a3247d-a5c0-458d-8f5b-2d364e2eeda3");
	    HttpResponse response = httpclient.execute(httpget);
	    try {
	    	HttpEntity entity = response.getEntity();
	        if (entity != null) {
	        	InputStream instream = entity.getContent();
	        	// String result = getStringFromInputStream(instream);
	            String text = IOUtils.toString(instream, StandardCharsets.UTF_8.name());
	            JSONArray jsonArray = new JSONArray(text); 
	            for (int i = 0; i < jsonArray.length(); i++) {
	                JSONObject explrObject = jsonArray.getJSONObject(i);
	                logger.info("JSON {}", explrObject);
	                double score = (double) explrObject.get("score");
	                if(score>=0.5){
	                	JSONObject char_array = explrObject.getJSONObject("charFragment");
	                	int begin = (int) char_array.get("start");
	                	int end = (int) char_array.get("end");
	                	logger.info("Begin: {}", begin);
	                	logger.info("End: {}", end);
	               
	                	Link l = new Link();
	                	l.begin = begin;
	                	l.end = end+1;
	                	l.link= (String) explrObject.get("DBpediaURL");
	                	links.add(l);
	                }
	            }
	          }
	        BufferedWriter buffWriter = new BufferedWriter(new FileWriter("/home/andokai/bachelorarbeit/Qanary-question-answering-components/qanary_component-NED-Babelfy/src/main/resources/questions.txt", true));
	        Gson gson = new Gson();
	        
	        String json = gson.toJson(links);
	        logger.info("gsonwala: {}",json);
	        
	        String MainString = myQuestion + " Answer: "+json;
	        buffWriter.append(MainString);
	        buffWriter.newLine();
	        buffWriter.close();
	      }
	      catch (ClientProtocolException e) {
	 		 logger.info("Exception: {}", e);
	         // TODO Auto-generated catch block
	     } catch (IOException e1) {
	     	logger.info("Except: {}", e1);
	         // TODO Auto-generated catch block
	     }
	    }
			
	    }
	    catch(FileNotFoundException e) 
		{ 
		    //handle this
			logger.info("{}", e);
		}
		logger.info("store data in graph {}", myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		// TODO: insert data in QanaryMessage.outgraph

		logger.info("apply vocabulary alignment on outgraph");
		// TODO: implement this (custom for every component)
		
		
		for (Link l : links) {
            String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
                    + "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
                    + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
                    + "INSERT { " + "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
                    + "  ?a a qa:AnnotationOfInstance . " //
                    + "  ?a oa:hasTarget [ " //
                    + "           a    oa:SpecificResource; " //
                    + "           oa:hasSource    <" + myQanaryQuestion.getUri() + ">; " //
                    + "           oa:hasSelector  [ " //
                    + "                    a oa:TextPositionSelector ; " //
                    + "                    oa:start \"" + l.begin + "\"^^xsd:nonNegativeInteger ; " //
                    + "                    oa:end  \"" + l.end + "\"^^xsd:nonNegativeInteger  " //
                    + "           ] " //
                    + "  ] . " //
                    + "  ?a oa:hasBody <" + l.link + "> ;" //
                    + "     oa:annotatedBy <urn:qanary:"+this.applicationName+"> ; " //
                    + "	    oa:annotatedAt ?time  " + "}} " //
                    + "WHERE { " //
                    + "  BIND (IRI(str(RAND())) AS ?a) ."//
                    + "  BIND (now() as ?time) " //
                    + "}";
            logger.debug("Sparql query: {}", sparql);
            myQanaryUtils.updateTripleStore(sparql, myQanaryMessage.getEndpoint().toString());
        }
		return myQanaryMessage;
	}
	
	class Link {
        public int begin;
        public int end;
        public String link;
    }
}
