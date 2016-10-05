package com.cmps128;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.cmps128.zookeeper.ZNodeCreate;

@Path("replica")
public class ReplicaManager {
	
	static void wait_for_update()
	{
    	while(Main.are_replicas_being_updated == true)
    	{
    		System.out.println("waiting dude");
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
	}

    @GET
    @Path("/get/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public String helloWorld(@PathParam("key") String key) {
    	wait_for_update();
    	if( key != null && KVS.key_val_store.containsKey(key))
    	{
    		return KVS.key_val_store.get(key);
    	}
    	else
    	{
    		return "kde";
    	}
    }

    @PUT
    @Path("/put/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String put(@PathParam("key") String key, @FormParam("val") String val) {
    	wait_for_update();
		Client client = ClientBuilder.newClient();
		System.out.println(Main.BASE_URI + "  above in leader put  KEY " + key);
    	MultivaluedMap<String, String> frmdata = new MultivaluedHashMap<>();
    	frmdata.add("val", val);
    	String result = null;
    	for(int i = 0; i < Main.all_uri.size(); i++)
    	{
    		if(Main.all_uri.get(i).equals(Main.BASE_URI))
    		{
    			LogItem item = new LogItem("put",key,val);
    			KVS.log.add(item);

    			KVS.key_val_store.put(key, val);
    			continue;
    		}
    		
    		if(!checkConnectivity(Main.all_uri.get(i) + "hello"))
    		{
    			continue;
    		}
    		
        	WebTarget target = client.target(Main.all_uri.get(i) + "replica/put/replicate/");
			Response response = target.path("/{key}").resolveTemplate("key", key).request()
					.put(Entity.form(frmdata));
      	    result = response.readEntity(String.class);
      	    //if(result != "success")
      	    System.out.println(response.getStatus());
    	}
    	System.out.println(Main.BASE_URI + "  in leader put ");
        return "success";
    }
    
    
    @PUT
    @Path("/put/replicate/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String store(@PathParam("key") String key, @FormParam("val") String value) {
    	LogItem item = new LogItem("put",key,value);
		KVS.log.add(item);
    	KVS.key_val_store.put(key, value);
    	System.out.println(Main.BASE_URI + "  in replication put ");
        return "success";
    }
    
    @DELETE
    @Path("/delete/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public String delete(@PathParam("key") String key) {
    	wait_for_update();
    	Client client = ClientBuilder.newClient();
    	String result = null;
    	String notexist = null;
    	for(int i = 0; i < Main.all_uri.size(); i++)
    	{
    		if(Main.all_uri.get(i).equals(Main.BASE_URI))
    		{
    			if(KVS.key_val_store.containsKey(key))
    			{
    				LogItem item = new LogItem("delete",key);
        			KVS.log.add(item);
    				KVS.key_val_store.remove(key);
    			}else{
    				notexist = "nosuchkey";
    			}
    			continue;
    		}
    		if(!checkConnectivity(Main.all_uri.get(i) + "hello"))
    		{
    			continue;
    		}
    		WebTarget target = client.target(Main.all_uri.get(i) + "replica/delete/replicate/" + key);
      		Response response = target.request().delete();
      		result = response.readEntity(String.class);
      		System.out.println("delete result for uri  is Main.all_uri.get(i) " + result);
    	}
    	if(notexist != null)
    	{
    		return notexist;
    	}
  		return result;
    }
    
    @DELETE
    @Path("/delete/replicate/{key}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public String remove(@PathParam("key") String key) {
    	if(KVS.key_val_store.containsKey(key))
    	{
			LogItem item = new LogItem("delete",key);
			KVS.log.add(item);
    		KVS.key_val_store.remove(key);
    	}
    	System.out.println(Main.BASE_URI + "  in replication delete ");
        return "success";
    }
    
    
    public static void syncdata()
    {
    	int max = 0, min = KVS.log.size();
    	ArrayList<IndexInfo> lastindices = new ArrayList<IndexInfo>();
    	String max_uri = null;
    	System.out.println("before syncing data");
    	for(int i = 0; i < Main.all_uri.size(); i++)
    	{
    		System.out.println("start for loop " + Main.all_uri.get(i));
    		if(Main.BASE_URI.equals(Main.all_uri.get(i)))
    		{
    			IndexInfo info = new IndexInfo(Main.all_uri.get(i), KVS.log.size());
    			lastindices.add(info);
    			if(KVS.log.size() > max)
    			{
    				max = KVS.log.size();
    				max_uri = Main.BASE_URI;
    			}
    			if(KVS.log.size() < min)
    			{
    				min = KVS.log.size();
    			}
    			continue;
    		}
    		String result = getLastLogIndex(Main.all_uri.get(i));
    		if(!result.contains("index")){
    			continue;
    		} else{
    			int pos = Integer.parseInt(result.split(":")[1]);
    			IndexInfo info = new IndexInfo(Main.all_uri.get(i), pos);
    			lastindices.add(info);
    			if(pos > max)
    			{
    				max = pos;
    				max_uri = Main.all_uri.get(i);
    			}
    			if(pos < min)
    			{
    				min = pos;
    			}
    		}
    		System.out.println("continue for loop");
    	}
    	System.out.println("before update min is " + min + " max is " + max);
    	if(max != min)
    	{
    		updatereplicas(max_uri, max, lastindices);
    	}    		
    }
    
    static void updatereplicas(String uri, int maxIndex, ArrayList<IndexInfo> indInfo)
    {
		if(!checkConnectivity(uri + "hello"))
		{
			return;
		}
    	Client client = ClientBuilder.newClient();
    	for(int i = 0; i < indInfo.size(); i++)
    	{
    		if(indInfo.get(i).lastIndex == maxIndex)
    		{
    			continue;
    		}
    		
    		if(!checkConnectivity(uri + "hello"))
    		{
    			return;
    		}
    		
	    	WebTarget target = client.target(uri + "replica/update");
	    	MultivaluedMap<String, String> formdata = new MultivaluedHashMap<>();
	    	formdata.add("uri", indInfo.get(i).uri);
			Response response = target.path("/{lastInd}").resolveTemplate("lastInd", indInfo.get(i).lastIndex).request()
					.put(Entity.form(formdata));
	  	    String result = response.readEntity(String.class);
	  	    System.out.println(response.getStatus());
	  	    System.out.println("tried updating replica " + indInfo.get(i).uri);
	  	    if(result.equals("success"))
	  	    	System.out.println(" updated replica " + indInfo.get(i).uri);
    	}
  	    //return result;
    }
    
    @PUT
    @Path("/update/{lastInd}")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String bring_uptodate(@PathParam("lastInd") int lastIndex, @FormParam("uri") String uri) {
		Client client = ClientBuilder.newClient();
		System.out.println(Main.BASE_URI + "  lasIndex " + lastIndex + " val " + uri);
		if(!checkConnectivity(uri + "hello"))
		{
			return "failure";
		}
    	String result = null;
    	for(int i = lastIndex; i < KVS.log.size(); i++)
    	{
			if(!checkConnectivity(uri + "hello"))
			{
				return "failure";
			}
    		if(KVS.log.get(i).op.equals("put"))
    		{
    			WebTarget target = client.target(uri + "replica/put/replicate/");
    			MultivaluedMap<String, String> frmdata = new MultivaluedHashMap<>();
    			frmdata.add("val", KVS.log.get(i).value);
    			Response response = target.path("/{key}").resolveTemplate("key", KVS.log.get(i).key).request()
    					.put(Entity.form(frmdata));
          	    result = response.readEntity(String.class);
          	    //if(result != "success")
          	    System.out.println(response.getStatus());
    		}
    		
    		if(KVS.log.get(i).op.equals("delete"))
    		{
				WebTarget target = client
						.target(uri + "replica/delete/replicate/" + KVS.log.get(i).key);
          		Response response = target.request().delete();
          		result = response.readEntity(String.class);
    		}
    	}
    	
        return "success";
    }
    
    static boolean checkConnectivity(String uri)
    {
    	URL passurl;
		try {
			passurl = new URL(uri);
	    	HttpURLConnection conn = (HttpURLConnection) passurl.openConnection();
	    	//conn.setRequestMethod("GET");
	    	conn.setConnectTimeout(500);
	    	conn.setReadTimeout(500);

			if (conn.getResponseCode() != 200) {
				System.out.println("failed in log index");
				return false;				
			} else{
				System.out.println("success in log index");
				return true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("failed in log index exception");
			return false;
		}
    }
    
    static String getLastLogIndex(String uri)
    {
    	if(!checkConnectivity(uri + "replica/get/lastindex"))
    	{
    		return "failure";
    	}

    	
        Client client = ClientBuilder.newClient();
      	WebTarget target = client.target(uri + "replica/get/lastindex");
      	Response response = target.request().get();
      	String result = response.readEntity(String.class);
      	System.out.println("get index result is " + result);
      	return result;
    }
    
    @GET
    @Path("/get/lastindex")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.TEXT_PLAIN)
    public String getindex(@PathParam("key") String key) {
    	return "index:"+KVS.log.size();
    }
    
    /*static void setReachableURIs()
    {
    	Main.all_uri = new ArrayList<String>();
    	for(int i = 0; i < Main.fixeduri.size(); i++)
    	{
    		if(checkConnectivity(Main.fixeduri.get(i) + "hello"))
    		{
    			Main.all_uri.add(Main.fixeduri.get(i));
    		}
    	}
    }*/
	
}

class IndexInfo
{
	String uri = null;
	int lastIndex = 0;
	public IndexInfo(String uri, int lastIndex) {
		this.uri = uri;
		this.lastIndex = lastIndex;
	}
}
