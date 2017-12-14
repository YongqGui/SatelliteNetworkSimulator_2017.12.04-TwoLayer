/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import satellite_orbit.SatelliteOrbit;
import interfaces.SimpleSatelliteInterface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import Cache.File;
import movement.MovementModel;
import movement.Path;
import movement.SatelliteMovement;
import routing.GridRouter;
import routing.MessageRouter;
import routing.util.RoutingInfo;
import Cache.CacheRouter;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	
	
	/**�洢���˶�ģ�͵õ���200����ά�����200����γ�����꣬ͼ�ν��滭ͼ*/
	int steps = 200;
	int step = 0;
	double max = 0;
	private double[][] XYZ = new double[steps][3];
	double[][] three_dem_points = new double[1][3];
	
	private double[][] BL = new double[steps][2];
	double[][] two_dem_points = new double[1][2];
	/**�洢���˶�ģ�͵õ���200����ά�����200����γ�����꣬ͼ�ν��滭ͼ*/
	
	/**��¼���ǽڵ�����*/
	private int order_;
	/**�޸ĺ�������**/
	private  double []parameters= new double[6];
	private Neighbors nei;		//����;
	//private GridNeighbors GN;
	
	/** namespace for host group settings ({@value})*/
	public static final String GROUP_NS = "Group";
	/** number of hosts in the group -setting id ({@value})*/
	public static final String NROF_HOSTS_S = "nrofHosts";
	
	private List<DTNHost> hosts = new ArrayList<DTNHost>();				//ȫ�����ǽڵ��б�
	private List<DTNHost> hostsinCluster = new ArrayList<DTNHost>();	//ͬһ�����ڵĽڵ��б�
	private List<DTNHost> hostsinMEO = new ArrayList<DTNHost>();		//�������ǵĽڵ��б�
	
	private int totalSatellites;		//�ܽڵ���
	private int totalPlane;				//��ƽ����
	private int nrofPlane;				//�����������ƽ����
	private int nrofSatelliteINPlane;	//�����ڹ��ƽ���ڵı��
	private int ClusterNumber;			//�����ڵ��������Ĵ����
	
	private HashMap<Integer, List<DTNHost>> ClusterList = new HashMap<Integer, List<DTNHost>>();
	/**�޸Ĳ�������**/
	
	/**------------------------------   �� DTNHost ��ӵı���       --------------------------------*/

	/** file�о���Я�������� */
	private HashMap<String,Integer> files;	
	/** ��һ��FileBuffer �����ݽ��д洢 */
	private HashMap<String,File> FileBuffer;
	/** ��һ��ChunkBuffer �����ݽ��л��� */
	private HashMap<String, HashMap<String,File>> ChunkBuffer = new HashMap<String, HashMap<String,File>>();
	/** �󶨻���·�� */
	private CacheRouter cacherouter;
	/** routing parameter */
	public static boolean multiThread;
	
	
	/**------------------------------   ��  DTNHost ��ӵı���       --------------------------------*/
	
	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus, 
			MovementModel mmProto, MessageRouter mRouterProto) {
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}	

		// TODO - think about the names of the interfaces and the nodes

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
	}
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;	
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is actively moving (false if not)
	 * @return true if this node is actively moving (false if not)
	 */
	public boolean isMovementActive() {
		return this.movement.isActive();
	}
	
	/**
	 * Returns true if this node's radio is active (false if not)
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive() {
		/* TODO: make this work for multiple interfaces */	
		return this.getInterface(1).isActive();
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}
	
	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}
	
    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host. 
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}

	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer 
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface: "+interfaceNo + 
					" at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;	
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, 
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);
			
			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : 
				"Interface types do not match.  Please specify interface type explicitly";
		}
		
		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		System.err.println(
				"WARNING: using deprecated DTNHost.connect(DTNHost)" +
		"\n Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
		if (!isRadioActive()) {
			// Make sure inactive nodes don't have connections			
			tearDownAllConnections();
			return;
		}
		
		if (simulateConnections) {
			if (multiThread)
				multiThreadInterfaceUpdate();
			for (NetworkInterface i : net) {
				i.update();
			}
		}
			this.router.update();
	}
	/**
	 * 
	 */
	public void multiThreadInterfaceUpdate(){
		int nrofCPU = Runtime.getRuntime().availableProcessors();// adjust thread pool size according to the computer platform
		//Create a thread pool
		//ExecutorService pool = Executors.newFixedThreadPool(nrofCPU);
		ExecutorService pool = Executors.newCachedThreadPool();
		//create a task list contains return value
		List<Callable> threadList = new ArrayList<Callable>();
		List<Future<Boolean>> resultList = new ArrayList<Future<Boolean>>();
		
		for (NetworkInterface ni : net) {
			threadList.add(new SingleCallable(ni.toString(), ni));
		}
		for (int i = 0; i < threadList.size(); i++) {
			//execute the task and acquire 'future' object
			Future<Boolean> future = pool.submit(threadList.get(i));
			resultList.add(future);
			
			//acquire the return value from 'future' object

//			if (resultList.size() >= nrofCPU) {
//				boolean done = false;
//				while (!done) {
//					for (Future<Boolean> f : resultList) {
//						if (f.isDone()) {
//							done = true;
//							resultList.remove(f);
//						}
//					}
//				}
//			}
		}
		boolean done = false;
		while (!done){
			if (resultList.get(resultList.size() - 1).isDone())
				done = true;
		}
		pool.shutdown();
	}
	
	/**
	 * Enable or disable the multiThread method in the simulation according to user's setting 
	 */
	public static void setMultiThread(){
		Settings s = new Settings("userSetting");
		multiThread = s.getBoolean("multiThread");
	}
	/**
	 * Define the task in the thread pool, i.e., the calculation process of one node
	 */
	class SingleCallable implements Callable<Object> {
		private String taskNum;
		private NetworkInterface inf;
		SingleCallable(String taskNum, NetworkInterface inf) {
			this.taskNum = taskNum;
			this.inf = inf;
		}
		/**
		 * Execute the specific task in thread pool.
		 */
		public Object call() throws Exception {
			((SimpleSatelliteInterface)this.inf).multiThreadUpdate();
			return true;
		}
		/**
		 * Returns a string presentation of the host.
		 * @return Host's name
		 */
		public String toString() {
			return taskNum;
		}
	}
	
	
	
	/** 
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections() {
		for (NetworkInterface i : net) {
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0) continue;
			
			// Destroy all connections
			List<NetworkInterface> removeList =
				new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns) {
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList) {
				i.destroyConnection(inf);
			}
		}
	}
	

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from); 

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}
		return retVal;	
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
		this.router.createNewMessage(m);
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}
	
	/**------------------------------   ��  DTNHost ��ӵĺ�������       --------------------------------*/	
	/**����ά����ת��Ϊ��ά����*/
	public double[][] convert3DTo2D(double X, double Y, double Z) {
		double[][] bl = new double[1][2]; 
		if(X>0) {
			bl[0][0] = Math.atan(Y/X)*180/3.1415926;
		}
		else if(X<0&&Y>0) {
			bl[0][0] = (3.1415926+Math.atan(Y/X))*180/3.1415926;
		}
		else if (X<0&&Y<0) {
			bl[0][0] = -(3.1415926-Math.atan(Y/X))*180/3.1415926;
		}
		
		bl[0][1] = Math.atan(Z/Math.sqrt(X*X+Y*Y))*180/3.1415926/**(201.5)*/;
		
		return bl;
	}
	/**����ά����ת��Ϊ��ά����*/
	
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	public void print(double t, double[] y) {

		// add data point to the plot
		if (step < XYZ.length) {
			XYZ[step][0] = y[0];
			XYZ[step][1] = y[1];
			XYZ[step][2] = y[2];
			
			if (y[0] > max)
				max = y[0];
			if (y[1] > max)
				max = y[1];
			if (y[2] > max)
				max = y[2];
			
			step++;
		}
	}
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	public void print1(double t, double[] y) {
		
	}
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	public void print2(double t, double[] y) {
		
	}
	/**�õ��˶�ģ�Ͳ�����200����ά���꺯����ʵ����Printable*/
	
	public double getMAX() {
		return this.max;
	}
	
	public double[][] getXYZ() {
		return this.XYZ;
	}
	
	public double[][] getBL() {
		return this.BL;
	}
	
	public double[][] get3Dpoints() {
		return this.three_dem_points;
	}
	
	public double[][] get2Dpoints() {
		return this.two_dem_points;
	}
	
	public int getOrder() {
		return this.order_;
	}

	/**------------------------------   ��  DTNHost ��ӵĺ�������       --------------------------------*/	
	
	/**�޸ĺ�������**/
	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {		
		double possibleMovement;
		double distance;
		double dx, dy;
		this.location.setLocation3D(((SatelliteMovement)this.movement).getSatelliteCoordinate(SimClock.getTime()));
	}	
	/**
	 * calculate the Orbit coordinate parameters
	 * @param parameters
	 * @param time
	 * @return
	 */
	public double[] calculateOrbitCoordinate(double[] parameters, double time){
		return ((SatelliteMovement)this.movement).calculateOrbitCoordinate(parameters, time);
	}
	
	/**
	 * get the movement model
	 * @return
	 */
	public MovementModel getMovementModel(){
		return this.movement;
	}
	
	/*��������*/
	//public GridNeighbors getGridNeighbors(){
	//	return GN;
	//}
	
	public void setSatelliteParameters(int totalSatellites, int totalPlane, int nrofPlane, int nrofSatelliteInPlane, double[] parameters){
		
		for (int i = 0; i < 6; i++){
			this.parameters[i] = parameters[i];
		}
		
		this.nei = new Neighbors(this);//����		
		
		/*��������*/
		this.totalSatellites = totalSatellites;//�ܽڵ���
		this.totalPlane = totalPlane;//���ƽ����
		this.nrofPlane = nrofPlane;//�����������ƽ����
		this.nrofSatelliteINPlane = nrofSatelliteInPlane;//�����ڹ��ƽ���ڵı��
		
		((SatelliteMovement)this.movement).setOrbitParameters(parameters);
		this.location.my_Test(0.0,0.0,this.parameters);//�޸Ľڵ�ĳ�ʼ��λ�ú���,��ȡt=0ʱ�̵�λ��
	}
	/**
	 * test the orbit parameters
	 * @param parameters
	 */
	public void SetSatelliteParametersTest(double[] parameters){
		for (int i = 0; i < 6; i++){
			this.parameters[i] = parameters[i];
		}
		this.location.my_Test(0.0,0.0,this.parameters);//�޸Ľڵ�ĳ�ʼ��λ�ú���,��ȡt=0ʱ�̵�λ��
	}
	
	/**
	 * ��ʼ��ʱ���ı䱾�ڵ����ڵĴ����
	 * @param num
	 */
	public void changeClusterNumber(int num){
		this.ClusterNumber = num;
	}
	/**
	 * ��ȡ���ڵ����ڵĴ����
	 * @return
	 */
	public int getClusterNumber(){
		return this.ClusterNumber;
	}
	/**
	 * ����ȫ�ָ������ڶ�Ӧ�Ľڵ��б�
	 * @return
	 */
	public HashMap<Integer, List<DTNHost>> getClusterList(){
		return this.ClusterList;
	}
	/**
	 * ���ر����ڵĽڵ��б�
	 * @return
	 */
	public List<DTNHost> getHostsinthisCluster(){
		return this.hostsinCluster;
	}
	/**
	 * ����MEO�������ǽڵ���б�
	 * @return
	 */
	public List<DTNHost> getMEOList(){
		return this.hostsinMEO;
	}
	/**
	 * ���������������ƽ���Ų���
	 */
	public int getNrofPlane(){
		return this.nrofPlane;
	}
	/**
	 * ���������ڹ��ƽ���ڵı��
	 */
	public int getNrofSatelliteINPlane(){
		return this.nrofSatelliteINPlane;
	}
	/**
	 * ����Neighbors�����ھӽڵ�����ʱ��ʱ��
	 * @param time
	 * @return
	 */
	public Coord getCoordinate(double time){
		double[][] coordinate = new double[1][3];
		//double[] t = new double[]{8000,0.1,15,0.0,0.0,0.0};;

		SatelliteOrbit saot = new SatelliteOrbit(this.parameters);
		//saot.SatelliteOrbit(t);
		coordinate = saot.getSatelliteCoordinate(time);
		Coord c = new Coord(0,0);
		c.resetLocation((coordinate[0][0])+40000, (coordinate[0][1])+40000, (coordinate[0][2])+40000);
		return c;
	}
	public double getPeriod(){

		SatelliteOrbit saot = new SatelliteOrbit(this.parameters);
		//saot.SatelliteOrbit(t);
		double period = saot.getPeriod();
		return period;
	}
	/**
	 * ���������������������ھ����ݿ�
	 * @return
	 */
	public Neighbors getNeighbors(){
		return this.nei;
	}

	/**
	 * �����������������������ǹ������
	 * @return
	 */
	public double[] getParameters(){
		return this.parameters;
	}
	public void changeHostsClusterList(HashMap<Integer, List<DTNHost>> hostsinEachPlane){
		this.ClusterList = hostsinEachPlane;
	}
	/**
	 * �ı�ȫ�ֽڵ��б�
	 * @param hosts
	 */
	public void changeHostsList(List<DTNHost> hosts){
		 this.nei.changeHostsList(hosts);
		 //this.GN.setHostsList(hosts);
		 //this.router.setTotalHosts(hosts);
		 this.hosts = hosts;
	}
	/**
	 * �ı䱾���ڽڵ��б���ʼ����
	 * @param hostsinCluster
	 */
	public void changeHostsinCluster(List<DTNHost> hostsinCluster){
		this.hostsinCluster = hostsinCluster;
	}
	/**
	 * �ı�MEO����ڵ��б���ʼ����
	 * @param hostsinMEO
	 */
	public void changeHostsinMEO(List<DTNHost> hostsinMEO){
		this.hostsinMEO = hostsinMEO;
	}
	/**
	 * ���±��ڵ�ָ��ʱ���λ������
	 * @param timeNow
	 */
	public void updateLocation(double timeNow){
		this.location.my_Test(0.0,timeNow,this.parameters);//�޸Ľڵ��λ��,��ȡtimeNowʱ�̵�λ��
	}
	/**
	 * ͨ���˺�������·��Э���������������ȫ�ֽڵ��б�
	 * @return ����ȫ�ֽڵ��б�
	 */
	public List<DTNHost> getHostsList(){
		List<DTNHost> totalhosts = new ArrayList<DTNHost>();
		totalhosts = this.hosts;
		return totalhosts;
	}
	/**
	 * ��ѡ��gridRouterʱ����Ҫ���г�ʼ������������ǰ�������й����Ϣ
	 */
	public void initialzationRouter(){
		Settings s = new Settings(GROUP_NS);
		String routerType = s.getSetting("router");//�ܽڵ���
		String option = s.getSetting("Pre_or_onlineOrbitCalculation");//�������ļ��ж�ȡ���ã��ǲ��������й����в��ϼ���������ķ�ʽ������ͨ����ǰ���������洢�����ڵ�Ĺ����Ϣ
		
		HashMap<String, Integer> orbitCalculationWay = new HashMap<String, Integer>();
		orbitCalculationWay.put("preOrbitCalculation", 1);
		orbitCalculationWay.put("onlineOrbitCalculation", 2);
		
		switch (orbitCalculationWay.get(option)){
		case 1://ͨ����ǰ���������洢�����ڵ�Ĺ����Ϣ���Ӷ����й����в��ٵ��ù�����㺯����Ԥ�����ͨ��������Ԥ��
			((GridRouter)this.router).initialzation();
			break;
		case 2:		
			break;
		}

	}
	
	/**------------------------------   ��  DTNHost ��ӵĺ�������       --------------------------------*/	
	
	/** ��ȡDTNHost�е�chunkBuffer*/
	public HashMap<String, HashMap<String,File>> getChunkBuffer() {
		return ChunkBuffer;
	}
	
	/** �Զ���ı���д���*/
	public void setFiles(HashMap<String, Integer> files) {
		this.files = files;
	}
	/** ��ȡ�ڵ��д�ŵĹ���file�ı�*/
	public HashMap<String, Integer> getFiles() {
		return files;
	}

	/** �Զ���Ļ��������д���*/
	public void setFileBuffer(HashMap<String, File> FileBuffer) {
		this.FileBuffer = FileBuffer;
	}
	/** ��ȡ�ļ��Ļ���*/
	public HashMap<String, File> getFileBuffer() {
		return FileBuffer;
	}
	
	/** ������FileBuffer����û���ļ�  */
	public File getFileBufferForFile(Message aMessage) {

		if (this.FileBuffer.containsKey(aMessage.getFilename()))
			return this.FileBuffer.get(aMessage.getFilename());
		else
			return null;
		//return this.FileBuffer.get(aMessage.getFilename());
	}
	/** gyq_test 2016/07/08     ���ڵõ���ǰ�ڵ㻺���ʣ��ռ�  */
	public int getFreeFileBufferSize(){
		int occupancy = 0;		

		if (this.router.getBufferSize() == Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		for (File File: getFileCollection()){
			occupancy += File.getSize();
		}

		return this.cacherouter.getFileBufferSize() - occupancy;
	}
	
	/** �õ����Ǵ���ļ���fileBuffersize  */
	public int getFileBufferSize(){
		return this.cacherouter.getFileBufferSize();
	}
	
	/** gyq_test 2016/07/08   ����ɾ�������б�����ʱ����õ��ļ�  */
	public boolean makeRoomForNewFile(int size){
		if (size > this.cacherouter.getFileBufferSize()) {
			return false; 										// message too big for the buffer
		}
		int freeBuffer = this.getFreeFileBufferSize();
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {			
			File File = getNextFileToRemove(true);
			if (File == null) {
				return false; 									// couldn't remove any more messages
			}			
			/* delete message from the buffer as "drop" */
			deleteFile(File.getId(), true);
			freeBuffer += File.getSize();
		}
		return true;
	}
	
	/** gyq_test 2016/07/08    �ҵ�������ʱ������ļ�����Ϊ��һ�����Ƴ��ļ�   */
	protected File getNextFileToRemove(boolean excludeMsgBeingSent){
		Collection<File> filebuffer = this.getFileCollection();
		File oldest = null;
		for (File f: filebuffer){
			if(!f.getInitFile()){          // ������ǳ�ʼ��������ļ�������ִ��
				if (oldest == null ) {
					oldest = f;
				}
				else if (oldest.getTimeRequest()> f.getTimeRequest()) {
					oldest = f;
				}
			}
		}
		return oldest;
	}
	
	/**  gyq_test 2016/07/08    ɾ���ڵ�buffer���ļ�    */
	public void deleteFile(String id, boolean drop) {
		File removed = removeFromFileBuffer(id);
		if (removed == null) throw new SimError("no file for id " +
				id + " to remove at " + this.getAddress());
		
		//for (MessageListener ml : this.mListeners) {
		//ml.messageDeleted(removed, this.getAddress(), drop);
		//}														                      // ����ʱ�������
	}
	
	/** gyq_test 2016/07/08    ���ڴӵ�ǰ�ڵ㻺��ռ���ɾ���ļ�   */
	public File removeFromFileBuffer(String id){
		File f= this.FileBuffer.remove(id);
		return f;
	}
	
	/**���ŵ�ǰ��Ϣ�����ȷ����Ϣ�б���  */
//	public void putIntoJudgeForRetransfer(Message m){
//		this.cacherouter.putJudgeForRetransfer(m);		
//	}
	
	/** gyq_test 2016/07/08      ���ڵõ���ǰ�ڵ��fileCollection */
	public Collection<File> getFileCollection(){
		return this.FileBuffer.values();
	}
	
	/** Ϊ��ǰ�ڵ����û���·��  CacheRouter */
	/**
	 * Set a CacheRouter for this host
	 * @param cacherouter The router to set
	 */
	public void setRouter(CacheRouter cacherouter) {
		cacherouter.init(this, msgListeners);
		this.cacherouter = cacherouter;
	}
	public CacheRouter getCacheRouter(){
		return this.cacherouter;
	}
	/**------------------------------   ��  DTNHost ��ӵĺ�������       --------------------------------*/	
	
    /**
     * @return satellite type in multi-layer satellite networks: LEO, MEO or GEO
     */
    public String getSatelliteType(){
    	return ((SatelliteMovement)getMovementModel()).getSatelliteType();
    }
}
