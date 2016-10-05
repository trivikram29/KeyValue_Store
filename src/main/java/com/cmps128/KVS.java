package com.cmps128;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.glassfish.jersey.server.ContainerRequest;

import com.cmps128.zookeeper.ZNodeCreate;

@Path("kvs")
public class KVS {
	static HashMap<String, String> key_val_store = new HashMap<String, String>();
	static ArrayList<LogItem> log = new ArrayList<LogItem>();
	String success= "success", error = "error", kde = "key does not exist";
	//public static boolean are_replicas_being_updated = false;
	
    @GET
    @Path("/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    public Response helloWorld(@PathParam("key") String key) {
    	updateLeader();
    	Message reply = new Message();
    	ResponseBuilder responseBuilder = null;
    	if(Main.BASE_URI.equals(Main.leader_uri))
    	{
    		ReplicaManager.wait_for_update();
	    	if( key != null && key_val_store.containsKey(key))
	    	{
	    		reply.setMsg(success);
	    		reply.setValue(key_val_store.get(key));
	    		responseBuilder = Response.status(200);
	    	}
	    	else
	    	{
	    		reply.setMsg(error);
	    		reply.setError(kde);
	    		responseBuilder = Response.status(404);
	    	}
    	} else
    	{
    		String str = passtoLeader_get(key);
    		if(!str.equals("kde"))
    		{
    			System.out.println("in get not null   str is " + str);
	    		reply.setMsg(success);
	    		if(!key_val_store.containsKey(key) || !(key_val_store.get(key).equals(str)))
	    		{
	    			System.out.println("updated replica");
	    			key_val_store.put(key, str);
	    		}
	    		reply.setValue(str);
	    		responseBuilder = Response.status(200);
    		} else
    		{
    			System.out.println("in get null");
	    		reply.setMsg(error);
	    		reply.setError(kde);
	    		responseBuilder = Response.status(404);
    		}
    	}
    	return responseBuilder.entity(reply).build();
    }

    @PUT
    @Path("/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response put(ContainerRequestContext context) {
    	
    	MultivaluedMap<String, String> pathparams = context.getUriInfo().getPathParameters();
    	String key = pathparams.getFirst("key");
    	ContainerRequest contreq = (ContainerRequest) context;
        contreq.bufferEntity();
        Form form = contreq.readEntity(Form.class);
        MultivaluedMap<String, String> formparams = form.asMap();
        String value = formparams.getFirst("val");
    	
    	
    	ResponseBuilder responseBuilder;
    	Message_value reply = new Message_value();
    	if(value == null || key == null || key.length() < 1 || key.length() > 250)
    	{
    		reply.setMsg(error);
    		reply.setError("Invalid key or value");
    		responseBuilder = Response.status(500);
    		return responseBuilder.entity(reply).build();
    	}
    	updateLeader();
    	
    	if(key_val_store.containsKey(key))
    	{
    		responseBuilder = Response.status(200);
    		reply.setReplaced(1);
    		reply.setMsg(success);
    	}
    	else
    	{
    		responseBuilder = Response.status(201);
    		reply.setReplaced(0);
    		reply.setMsg(success);
    	}
    	if(Main.leader_uri.equals(Main.BASE_URI))
    	{
    		key_val_store.put(key, value);
    		passtoLeader_put(key, value);
    	}else
    	{
    		passtoLeader_put(key, value);
    	}
    	System.out.println("reached end of put");
    	
        return responseBuilder.entity(reply).build();
    }
    
    String passtoLeader_get(String key)
    {
  		Client client = ClientBuilder.newClient();
  		if(!ReplicaManager.checkConnectivity(Main.leader_uri + "hello"))
    	{
    		return "failure";
    	}
  		WebTarget target = client.target(Main.leader_uri + "replica/get/" + key);
  		Response response = target.request().get();
  		String result = response.readEntity(String.class);
  		System.out.println("get result is " + result);
  		return result;
    }
    
    String passtoLeader_put(String key, String val)
    {
    	Client client = ClientBuilder.newClient();
    	if(!ReplicaManager.checkConnectivity(Main.leader_uri + "hello"))
    	{
    		return "failure";
    	}
    	WebTarget target = client.target(Main.leader_uri + "replica/put");
    	MultivaluedMap<String, String> formdata = new MultivaluedHashMap<>();
    	formdata.add("val", val);
		Response response = target.path("/{key}").resolveTemplate("key", key).request()
				.put(Entity.form(formdata));
  	    String result = response.readEntity(String.class);
  	    System.out.println(response.getStatus());
  	    System.out.println(" result is " + result);
  	    return result;
    }
    
    @DELETE
    @Path("/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    public Response delete(@PathParam("key") String key) {
    	updateLeader();
    	Message reply = new Message();
    	ResponseBuilder responseBuilder = null;
    	String result = passtoLeader_delete(key);
		if (result.equals(success)) {
			reply.setMsg(success);
			// key_val_store.remove(key);
			
			responseBuilder = Response.status(200);
		} else {
			reply.setMsg(error);
			reply.setError(kde);
			responseBuilder = Response.status(404);
		}
    	return responseBuilder.entity(reply).build();
    }
    
    String passtoLeader_delete(String key)
    {
  		Client client = ClientBuilder.newClient();
  		if(!ReplicaManager.checkConnectivity(Main.leader_uri + "hello"))
  		{
  			return "failure";
  		}
  		WebTarget target = client.target(Main.leader_uri + "replica/" + "delete/" + key);
  		Response response = target.request().delete();
  		String result = response.readEntity(String.class);
  		System.out.println("delete result is " + result);
  		return result;
    }
    
    void updateLeader()
    {
    	Stat stat = null;
    	try {
    		ZNodeCreate.zk.sync(ZNodeCreate.path, null, null);
    		stat = ZNodeCreate.zk.exists(ZNodeCreate.path, false);
    		List<String> allchildpaths = null;
    		if(stat != null)
    		{
    			allchildpaths = ZNodeCreate.zk.getChildren(ZNodeCreate.path, false);
    		}else{
    			ZNodeCreate.start();
    			updateLeader();
    		}
    		
    		if(allchildpaths == null){
    			System.out.println("no children exist");
    			ZNodeCreate.start();
    			updateLeader();
    		}else{
    			Collections.sort(allchildpaths);
    			byte[] uri = ZNodeCreate.zk.getData(ZNodeCreate.path+"/"+allchildpaths.get(0), false, null);
    			Main.leader_uri = new String(uri, "UTF-8");
    			System.out.println("updated leader is " + Main.leader_uri);
    		}
		} catch (InterruptedException | UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			System.out.println("-------------------NO hello stat-----------------------------------");
			e.printStackTrace();
			
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			if(stat == null)
			{
				System.out.println("-------------------hello stat-----------------------------------");
				ZNodeCreate.start();
				updateLeader();
			}
		}
    }
    
    

}

class LogItem
{
	String op = null;
	String key = null, value = null;
	public LogItem(String op, String key,String val) {
		this.op = op;
		this.key = key;
		this.value = val;
	}
	public LogItem(String op, String key) {
		this.op = op;
		this.key = key;
	}
}

