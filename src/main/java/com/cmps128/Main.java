package com.cmps128;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import com.cmps128.zookeeper.ZNodeCreate;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static String BASE_URI = "http://0.0.0.0:8080/";
    public static ArrayList<String> all_uri = new ArrayList<String>();
    public static String leader_uri = null;
/*    static String member1 = "http://0.0.0.0:8081/";
    static String member2 = "http://0.0.0.0:8082/";*/
    public static boolean are_replicas_being_updated = false;
    public static CountDownLatch replicas_updating;

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() {
        final ResourceConfig rc = new ResourceConfig().packages("com.cmps128");
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }
    
    public static void startZookeeperserver()
    {
    	//String str[] = System.getenv("MEMBERS").split(",");
    	Properties zserverprop = new Properties();
    	zserverprop.setProperty("tickTime", "2000");
    	zserverprop.setProperty("clientPort", "2180");
    	//zserverprop.setProperty("serverId", "1");
    	
    	String dataDirectory = System.getProperty("java.io.tmpdir");
    	File dir = new File(dataDirectory, "zookeeper0").getAbsoluteFile();
    	if(!dir.exists())
    	{
    		dir.mkdir();
    	}
    	zserverprop.setProperty("dataDir", dir.getAbsolutePath());
    	System.out.println(dir.getAbsolutePath());
    	FileWriter fw;
		try {
			fw = new FileWriter(dir.getAbsolutePath()+"/myid");
			//fw.write("1"); // Without Docker
			fw.write(getServerId()+"");
			fw.flush();
			fw.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
    	
		
    	zserverprop.setProperty("initLimit", "5");
    	zserverprop.setProperty("syncLimit", "2");
/*    	zserverprop.setProperty("server.1", "localhost:2888:3888");
    	zserverprop.setProperty("server.2", "localhost:2889:3889");
    	zserverprop.setProperty("server.3", "localhost:2890:3890");*/
    	setznodeServers(zserverprop);
    	
    	final QuorumPeerConfig config = new QuorumPeerConfig();
    	//config.getse
    	try {
			config.parseProperties(zserverprop);
		} catch (IOException | ConfigException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	final QuorumPeerMain qpm = new QuorumPeerMain();
    	new Thread() {
    	    public void run() {
    	    	try {
    				qpm.runFromConfig(config);
//    				/qpm.main(args);
    			} catch (IOException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
    	    }
    	}.start();
    }
    
    static void setznodeServers(Properties zseverprop)
    {
    	String str[] = System.getenv("MEMBERS").split(",");
    	for(int i = 0; i < all_uri.size(); i++)
    	{
    		zseverprop.setProperty("server." + (i+1), str[i].split(":")[0] + ":2888:3888" );
    	}
    }
    
    static int getServerId()
    {
    	for(int i = 0; i < all_uri.size(); i++)
    	{
    		if(BASE_URI.equals(all_uri.get(i)))
    		{
    			return i+1;
    		}
    	}
    	return -1;
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    	getInfoFromEnvVaribales();
    	//comment when running with docker
/*    	all_uri.add(BASE_URI);
    	all_uri.add(member1);
    	all_uri.add(member2);
    	Collections.sort(all_uri);*/
    	//

    	
        final HttpServer server = startServer();
        ServerConfiguration sc = server.getServerConfiguration();
        sc.setMaxBufferedPostSize(20971520);
        sc.setMaxFormPostSize(20971520);
        sc.setMaxPostSize(20971520);
        sc.setMaxRequestParameters(20971520);
    	startZookeeperserver();
    	new Thread() {
    	    public void run() {
    	            ZNodeCreate.start();
    	    }
    	}.start();
	//	System.out.println(
	//			sc.getMaxBufferedPostSize() + "     " + sc.getMaxFormPostSize() + "    " + sc.getMaxPostSize());
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\n", BASE_URI));
    }
    
    static void getInfoFromEnvVaribales()
    {
    	String IPaddress = System.getenv("IP");
    	String portnum = System.getenv("PORT");
    	String str[] = System.getenv("MEMBERS").split(",");
    	for(int i = 0; i < str.length; i++)
    	{
    		all_uri.add("http://" + str[i] + "/");
    	}
    	Collections.sort(all_uri);
    	//leader_uri = all_uri.get(0);
    	if(IPaddress != null && portnum != null)
    	{
    		BASE_URI = "http://" + IPaddress + ":" + portnum + "/";
    	}
    }
}

