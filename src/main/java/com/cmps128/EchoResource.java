package com.cmps128;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.QueryParam;

@Path("echo")
public class EchoResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(@QueryParam("msg") String mssg){
    	if(mssg == null)
    	{
    		return "";
    	}
        if(mssg.matches("[A-Za-z0-9]*"))
        {
            return mssg;
        }
        else
        {
            return " Given argument is not alphanumeric ";
        }
        
    }

} 
