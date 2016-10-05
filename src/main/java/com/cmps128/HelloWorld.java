package com.cmps128;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("hello")
public class HelloWorld {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String helloWorld() {
        return "Hello world!";
    }
    
	//URL passurl = new URL(Main.leader_uri + "replica/" + "get/" + key);
		//HttpURLConnection conn = (HttpURLConnection) passurl.openConnection();
		//conn.setRequestMethod("GET");
		//conn.setRequestProperty("Accept", "application/json");

		/*if (conn.getResponseCode() != 200) {
			throw new RuntimeException("Failed : HTTP error code : "
					+ conn.getResponseCode());
		}

		BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

		StringBuilder builder = new StringBuilder();
		String output;
		//System.out.println("Output from Server .... \n");
		while ((output = br.readLine()) != null) {
			builder.append(output);
		}
		br.close();
		conn.disconnect();*/
}
