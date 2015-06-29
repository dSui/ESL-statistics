/*
 * Made by Piet S. (2015)
 * 
 * Info: Retrieves the latest World of Tank results and returns statistics from participants based on team size.
 * 
 * Frameworks used: Jersey and Java Restful Services (javax.ws.rs).
 * Used ning for non-blocking calls: https://github.com/AsyncHttpClient/async-http-client
 * 
 * Web server used: Apache Tomcat 7.0.62
 * Java version: 1.8.0_45
 * IDE: Eclipse
 * 
 * Read pom.xml for a list of all dependencies
 * 
 */

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.JSONObject;
import org.json.JSONArray;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Future;


@Path("/")
public class Rankings {

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public String getServices() {
		
		// Declare variables
		String leagueAPICall = "http://play.eslgaming.com/api/leagues?types=cup&states=finished&limit.total=25&path=%2Fplay%2Fworldoftanks%2Feurope%2F";
		String cupRankingPrefix = "http://play.eslgaming.com/api/leagues/";
		String cupRankingSuffix = "/ranking";
		String response = "[ ]"; // empty JSON array as default response
		AsyncHttpClient asyncHttpClient = null;

		try {
			
			// Retrieve the data from ESL API (non-blocking through the ning AsyncHttpClient library) and parse as JSON using org.json
			asyncHttpClient = new AsyncHttpClient();
			Future<Response> futureCallback = asyncHttpClient.prepareGet(leagueAPICall).execute();
			String cupList = futureCallback.get().getResponseBody();
			JSONObject cupListJson = new JSONObject(cupList.trim());
			
			// Memory collections for data processing
			HashMap<String, HashMap<String, Integer>> statistics = new HashMap<String, HashMap<String, Integer>>(); // key: participant ID, Value: HashMap (Key: teamSize, Value: participation count)
			
			// Iterate through all cups (Limited to 25 in API request)
			Iterator<?> cupIDs = cupListJson.keys();
			while( cupIDs.hasNext() ) {
			    String cupKey = (String)cupIDs.next();
			    
			    // process a cup object but only if it's a valid JSON object
			    if ( cupListJson.get(cupKey) instanceof JSONObject ) {
			    	JSONObject cupInfoJson = cupListJson.getJSONObject(cupKey);
			    	
			    	if (!cupInfoJson.has("id") || !cupInfoJson.has("teamSize")) // cant properly process this, skip it
			    		continue;
			    	
			    	int cupID = cupInfoJson.getInt("id");
			    	int teamSize = cupInfoJson.getInt("teamSize");
			    	String cupURL = cupRankingPrefix+cupID+cupRankingSuffix;
			    	
			    	// Retrieve the cup rankings from ESL API (non-blocking)
					futureCallback = asyncHttpClient.prepareGet(cupURL).execute();
					JSONObject cupRankingJson = new JSONObject(futureCallback.get().getResponseBody().trim());
					
					// Go through each participant in the rankings
					if (!cupRankingJson.has("ranking") || !(cupRankingJson.get("ranking") instanceof JSONArray)) // cant properly process this, skip it
						continue;
					JSONArray rankingsArrayJson = cupRankingJson.getJSONArray("ranking");
					
					for (int i = 0; i < rankingsArrayJson.length(); i++) {
						
						if (!(rankingsArrayJson.get(i) instanceof JSONObject)) // noticed in other game rankings that certain cups are not always played and then ranking is null
							continue;
						JSONObject rankInfo = rankingsArrayJson.getJSONObject(i);
						
						// depending on team size the API returns "team" or "user"
						String key = (teamSize == 1 ? "user" : "team");
						if (!rankInfo.has(key) || !(rankInfo.get(key) instanceof JSONObject))  // cant properly process this, skip it
							continue;
						JSONObject participant = rankInfo.getJSONObject(key);
						
						if (!participant.has("id"))  // cant properly process this, skip it
							continue;
						String participantID = participant.getString("id");
						
						// Update the count of this participant in leagues with this teamsize
						addParticipantCount(participantID, teamSize, statistics);

					}
			    }
			}
			
			response = generateOutput(statistics);
			
		} catch ( Exception e) {
			e.printStackTrace();
			response = "[ { \"Message\" : \"Something went wrong\", \"Details\" : \""+e.getMessage()+"\" } ]"; // return an array containing 1 error message
		} finally {
			asyncHttpClient.close();
		}
		
		return response;
	}
	
	// Generate JSON output. I first construct a compact JSON Object and then use org.json to return a prettyprinted JSON format to make it look better according to JSON standards.
	private String generateOutput(HashMap<String, HashMap<String, Integer>> statistics) {
		String resp = "[";
		for (String participant : statistics.keySet()) {
			resp += "{";
			resp += "\"contestantId\":" + participant + ",";
			resp += "\"cupsPlayed\":{";
			for (String teamSize : statistics.get(participant).keySet()) {
				resp += "\""+teamSize+"\":"+statistics.get(participant).get(teamSize) +",";
			}
			resp = resp.substring(0, resp.length()-1); // formatting: remove the "," from the last value in the list
			resp += "}";
			resp += "},";
		}
		resp = resp.substring(0, resp.length()-1); // formatting: remove the "," from the last value in the list
		resp += "]";
		
		JSONArray printer = new JSONArray(resp);
		return printer.toString(1); // Pretty!
	}
	
	// Increment the counter for a participant with {id} for leagues with {teamsize}
	private int addParticipantCount(String id, int teamSize, HashMap<String, HashMap<String, Integer>> statistics) {
    	String teamSizeKey = String.valueOf(teamSize); // I prefer working with Strings as keys in HashMaps instead of <Integer, Integer> (or a List indexed by teamSize)
    	
    	if (!statistics.containsKey(id))
			statistics.put(id, new HashMap<String,Integer>());
    	
    	int count = 0;
    	if (statistics.get(id).containsKey(teamSizeKey))
    		count = statistics.get(id).get(teamSizeKey);
    	count++;
    	
    	statistics.get(id).put(teamSizeKey, count);
    	
    	return count;
	}

}
