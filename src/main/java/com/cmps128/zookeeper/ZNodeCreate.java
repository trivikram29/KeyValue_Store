package com.cmps128.zookeeper;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.data.Stat;

import com.cmps128.KVS;
import com.cmps128.Main;
import com.cmps128.ReplicaManager;

public class ZNodeCreate {
   // create static instance for zookeeper class.
   public static ZooKeeper zk;
   private static String thischildpath = null, watchpath = null, currwatchpath; 
   public static String path = "/leaderelection";
   // create static instance for ZooKeeperConnection class.
   private static ZooKeeperConnection conn;

   public static Stat znode_exists(String path) throws
   KeeperException,InterruptedException {
   return zk.exists(path, true);
   }
   
   // Method to create znode in zookeeper ensemble
   public static void createPersistent(String path, byte[] data) throws 
      KeeperException,InterruptedException {
      zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
   }
   
	public static void createEphemeral(String path, byte[] data) throws KeeperException, InterruptedException {
		thischildpath = zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
	}
	
	static void startLeaderElection() {
		List<String> allchildpaths = null;
		try {
			allchildpaths = zk.getChildren(path, false);
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		Collections.sort(allchildpaths);
		try {
			byte[] uri = zk.getData(path+"/"+allchildpaths.get(0), false, null);
			Main.leader_uri = new String(uri, "UTF-8");
		} catch (KeeperException | InterruptedException | UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		String str[] = thischildpath.split("/");
		int i = allchildpaths.indexOf(str[str.length-1]);
		
		if(i == 0)
		{
			System.out.println(" I became the leader " + Main.BASE_URI);
			Main.are_replicas_being_updated = true;
			//Main.replicas_updating = new CountDownLatch(1);
			ReplicaManager.syncdata();
			Main.are_replicas_being_updated = false;
		}
		else
		{
			watchpath = path + "/" + allchildpaths.get(i-1);
			System.out.println("watching node " + watchpath);
			try{
			zk.exists(watchpath, new Watcher(){
				public void process(WatchedEvent we) {
					
					final EventType eventType = we.getType();
					if(EventType.NodeDeleted.equals(eventType)) {
						if(we.getPath().equalsIgnoreCase(watchpath)) {
							startLeaderElection();
						}
					}
					
		         }
			});
			} catch(KeeperException | InterruptedException ex){
				ex.printStackTrace();
			}
			
			Main.are_replicas_being_updated = true;
			//Main.replicas_updating = new CountDownLatch(1);
			ReplicaManager.syncdata();
			Main.are_replicas_being_updated = false;
		}
		currwatchpath = path + "/" + allchildpaths.get(i);
		setcurrentnodewatchpath();
	}
	
	static void setcurrentnodewatchpath()
	{
		try{
			zk.exists(currwatchpath, new Watcher(){
				public void process(WatchedEvent we) {
					
					final EventType eventType = we.getType();
					if(EventType.NodeDeleted.equals(eventType)) {
						if(we.getPath().equalsIgnoreCase(currwatchpath)) {
							start();
						}
					}

		         }
			});
		} catch(KeeperException | InterruptedException ex){
			ex.printStackTrace();
		}
	}

   public static void start() {

      byte[] data = "created election znode".getBytes();
		
      try {
         conn = new ZooKeeperConnection();
         zk = conn.connect("localhost:2180");


         Stat stat = znode_exists(path);
			
         if(stat != null) {
        	 createEphemeral(path+"/n-", Main.BASE_URI.getBytes());
        	 
        	 startLeaderElection();
        	 
         } else {
        	createPersistent(path, data);
            System.out.println("Node is created");
       	    createEphemeral(path+"/n-", Main.BASE_URI.getBytes());
    	 
       	    startLeaderElection();
            
         }
         //conn.close();
      } catch (Exception e) {
         System.out.println(e.getMessage()); //Catch error message
      }
   }
}