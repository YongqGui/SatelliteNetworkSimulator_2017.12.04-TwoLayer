/*
 * Copyright 2016 University of Science and Technology of China , Infonet Lab
 * Written by LiJian.
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import core.*;
import movement.SatelliteMovement;
import util.Tuple;
import static core.SimClock.getTime;


public class ShortestPathFirstRouter extends ActiveRouter {
    /**
     * The TTL of confirm message -setting id ({@value})
     */
    public static final String HELLOTTL_S = "HelloTTL";
    /**
     * The size of confirm message -setting id ({@value})
     */
    public static final String HELLOMESSAGESIZE_S = "HelloMessageSize";
    /**
     * label indicates that the message can wait for next hop coming or not -setting id ({@value})
     */
    public static final String MSG_WAITLABEL = "waitLabel";
    /**
     * label indicates that routing path can contain in the message or not -setting id ({@value})
     */
    public static final String MSG_PATHLABEL = "msgPathLabel";
    /**
     * router path -setting id ({@value})
     */
    public static final String MSG_ROUTERPATH = "routerPath";
    /**
     * Group name in the group -setting id ({@value})
     */
    public static final String GROUPNAME_S = "Group";
    /**
     * interface name in the group -setting id ({@value})
     */
    public static final String INTERFACENAME_S = "Interface";
    /**
     * transmit range -setting id ({@value})
     */
    public static final String TRANSMIT_RANGE_S = "transmitRange";
    
    /** light speed��approximate 3*10^8m/s */
    private static final double LIGHTSPEED = 299792458;
    /** the interval of each hello process -setting id ({@value} */
    private double helloInterval;
    /** indicates the TTL of hello message -setting id ({@value} */
    private static int helloTtl;
    /** indicates the number of hello message -setting id ({@value} */
    private static int helloMessageNum;
    /** hello message size -setting id ({@value} */
    private static int helloMessageSize;
    
    /** store the latest hello check time */
    private double lastHelloCheckTime;
    /** indicate the transmission radius of each satellite */
    private static double transmitRange;
    /** label indicates that routing path can contain in the message or not */
    private static boolean msgPathLabel;
    /** label indicates that the static routing parameters are set or not */
    private static boolean initLabel = false;
    /** label indicates that routing algorithm has been executed or not at this time */
    private boolean routerTableUpdateLabel;
    /** maintain the earliest arrival time to other nodes */
    private HashMap<DTNHost, Double> arrivalTime = new HashMap<DTNHost, Double>();
    /** the router table comes from routing algorithm */
    private HashMap<DTNHost, List<Tuple<Integer, Boolean>>> routerTable = new HashMap<DTNHost, List<Tuple<Integer, Boolean>>>();
    /** to make the random choice */
    private Random random;
    
    private List<Message> helloMessages;
    
	/** �����жϰ������� */
	public String SelectLabel;
	/** Queue mode for sending messages */
	protected int sendQueueMode;
	

    public ShortestPathFirstRouter(Settings s) {
        super(s);
    }


    protected ShortestPathFirstRouter(ShortestPathFirstRouter r) {
        super(r);
    }


    @Override
    public MessageRouter replicate() {
        return new ShortestPathFirstRouter(this);
    }

    @Override
    public void init(DTNHost host, List<MessageListener> mListeners) {
        super.init(host, mListeners);
        if (!initLabel){
        	random = new Random();
            Settings setting = new Settings(INTERFACENAME_S);
            transmitRange = setting.getInt(TRANSMIT_RANGE_S);
            setting.setNameSpace(GROUPNAME_S);
            msgPathLabel = setting.getBoolean(MSG_PATHLABEL);
            initLabel = true;
            helloInterval = setting.getDouble("HelloInterval");
            helloTtl = setting.getInt(HELLOTTL_S);
            helloMessageSize = setting.getInt(HELLOMESSAGESIZE_S);
        }
    }
    
    /**
     * ��Networkinterface����ִ����·�жϺ���disconnect()�󣬶�Ӧ�ڵ��router���ô˺���
     */
    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
//		System.out.println("message: "+con);
//		if (!con.isUp()){
//			if(con.isTransferring()){
//				if (con.getOtherNode(this.getHost()).getRouter().isIncomingMessage(con.getMessage().getId()))
//					con.getOtherNode(this.getHost()).getRouter().removeFromIncomingBuffer(con.getMessage().getId(), this.getHost());
//				super.addToMessages(con.getMessage(), false);//������Ϊ��·�ж϶���ʧ����Ϣ�����·Żط��ͷ��Ķ����У�����ɾ���Է��ڵ��incoming��Ϣ
//				System.out.println("message: "+con.getMessage());
//			}
//		}
    }

    @Override
    public void update() {
        super.update();

        List<Connection> connections = this.getConnections();

        if (isTransferring()) { 	// judge the link is occupied or not
            return; 				// can't start a new transfer
        }
        if (connections.size() > 0) {//���ھ�ʱ��Ҫ����hello������Э��
            //helloProtocol();//ִ��hello����ά������
        }
        if (!canStartTransfer())
            return;

        /**Set router update label to make sure that routing algorithm only execute once at a time**/
        routerTableUpdateLabel = false;

        /** sort the messages to transmit */
        List<Message> messageList = this.CollectionToList(this.getMessageCollection());
        List<Message> messages = sortByQueueMode(messageList);
        
        for (Message msg : messages){
        	if(findPathToSend(msg) == true){
        		return;
        	}
        }
        
    }
    
    /** transform the message Collection to List
     * @param messages 
     * @return
     */
    
    public List<Message> CollectionToList(Collection<Message> messages){
    	List<Message> forMsg = new ArrayList<Message>();
        for (Message msg : messages) {	//���Է��Ͷ��������Ϣ
        	forMsg.add(msg);
        }
    	return forMsg;
    }
    
    /**
     * periodically send hello packet to neighbor satellite nodes to check their status
     */
    public void helloProtocol(){
        // TODO helloProtocol
    	List<Tuple<Message, Connection>> Collection = 
    				new ArrayList<Tuple<Message, Connection>>();
    	
    	if (SimClock.getTime() > getNextHelloTime()){ 
    		for (Connection con : this.getConnections()){
    			DTNHost to = con.getOtherNode(this.getHost());
        		Message m = createHelloMessage(this.getHost(), to, "Hello, num: " + (helloMessageNum++), helloMessageSize, 0);
        		Collection.add(new Tuple<Message, Connection>(m, con));     		
    		}
    		//simulate broadcast process
    		broadcastHelloMessage(Collection);
    	}
    }
    /**
     * Although The ONE does not have MAC layer, so it does not support broadcast,
     * this method can still be used to simulate the broadcast
     * @param connections
     * @return
     */
    public boolean broadcastHelloMessage(List<Tuple<Message, Connection>> Collections){
    	boolean tryLabel = false;
    	//TODO deleteMessage������startTransfer�����ڲ����ù�
    	//deleteMessage(m.getId(), true);
    	for (Tuple<Message, Connection> t : Collections){
    		if (sendMsg(t)){
    			tryLabel = true;
    		}
    	}

    	//TODO check this clear method
    	if (tryLabel)
    		helloMessages.clear();
    	return tryLabel;
    }
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);

		// 1.LEO satellite nodes receive process
		if (m.getProperty("Hello") != null &&
               (DTNHost) m.getProperty("Hello") == this.getHost()){
//            Object o = m.getProperty("ManageNode");
//		    if (! (o instanceof DTNHost))
//		        throw new SimError("Confirm message error!");
//		  //TODO change the sequence
//		    LEOci.addManageHosts((DTNHost) o);
//		    // send feedback
//            if (sendConfirmFeedback((DTNHost) o)) {
//                // add manage hosts
//            }
        }
		return m;
	}
	/**
	 * Creates a new confirm message from MEO node to LEO node
	 * @param from
	 * @param to
	 * @param id
	 * @param size
	 * @param responseSize
	 * @return
	 */
    public Message createHelloMessage(DTNHost from, DTNHost to, String id, int size, int responseSize){
		Message m = new Message(from, to, id, size);
		m.setResponseSize(responseSize);
		m.updateProperty("Hello", to);
		//m.updateProperty("HelloInfo", from);
		((ShortestPathFirstRouter)from.getRouter()).createNewMessage(m, helloTtl);
		helloMessages.add(m);
		
		return m;
    }
	/**
	 * Creates a new Confirm message to the router.
	 * The TTL of confirm message setting is different from normal message.
	 * @param m The message to create
	 * @return True if the creation succeeded, false if not (e.g.
	 * the message was too big for the buffer)
	 */
	public boolean createNewMessage(Message m, int Ttl) {
		m.setTtl(Ttl);
		addToMessages(m, true);		
		return true;
	}
    /**
     * @return next time to send confirm message to LEO satellite nodes
     */
    public double getNextHelloTime(){
    	if (lastHelloCheckTime <= 0)
    		return random.nextDouble()*(helloInterval/2);
    	else
    		return lastHelloCheckTime + helloInterval;
    }
    /**
     * Update router table, find a routing path and try to send the message
     *
     * @param msg
     * @param connections
     * @param msgPathLabel
     * @return
     */
    public boolean findPathToSend(Message msg) {
    	// if user allow routing path information written into the message header
        if (msgPathLabel == true) {
        	// if message header contains routing path information, this node should be intermediate node
            if (msg.getProperty(MSG_ROUTERPATH) == null) {
                Tuple<Message, Connection> t =
                        findPathFromRouterTabel(msg);
                //System.out.println(this.getHost()+" sending "+t+"  "+SimClock.getTime());
                return sendMsg(t);
            } else {
                Tuple<Message, Connection> t =
                        findPathFromMessage(msg);
                assert t != null : "Reading routing path from message header fail!";
                return sendMsg(t);
            }
        } else {
        	//don't write the routing path into the header
        	//routing path will be calculated in each hop
            Tuple<Message, Connection> t =
                    findPathFromRouterTabel(msg);
//            System.out.println("��Ϣ����Ԫ��Ϊ��"+t);
            return sendMsg(t);
        }
    }

    /**
     * Try to read the path information stored in the header. 
     * If the operation fails, the routing table should be re-calculated.
     * @param msg
     * @return
     */
    public Tuple<Message, Connection> findPathFromMessage(Message msg) {
    	List<Tuple<Integer, Boolean>> routerPath = null;
    	if (msg.getProperty(MSG_ROUTERPATH) instanceof List){
    		routerPath = (List<Tuple<Integer, Boolean>>) msg.getProperty(MSG_ROUTERPATH);
    	}
        int thisAddress = this.getHost().getAddress();
        if (msg.getTo().getAddress() == thisAddress){
//            throw new SimError("Message: " + msg +
//                    " already arrive the destination! " + this.getHost());  
        	System.out.println("Message: " + msg +
                  " already arrive the destination! " + this.getHost());
        }
        if (routerPath == null)
        	return null;
        
        //try to find the next hop from routing path in the message header
        int nextHopAddress = -1;
        boolean waitLable = false;
        for (int i = 0; i < routerPath.size(); i++) {
            if (routerPath.get(i).getKey() == thisAddress) {
                nextHopAddress = routerPath.get(i + 1).getKey();//�ҵ���һ���ڵ��ַ
                waitLable = routerPath.get(i + 1).getValue();//�ҵ���һ���Ƿ���Ҫ�ȴ��ı�־λ
                break;
            }
        }

        if (nextHopAddress > -1) {
            Connection nextCon = findConnection(nextHopAddress);
            //the routing path in the message header could be invaild
            if (nextCon == null) {
                if (!waitLable) {
                    msg.removeProperty(MSG_ROUTERPATH);
                    //try to re-routing
                    Tuple<Message, Connection> t =
                            findPathFromRouterTabel(msg);
                    return t;
                }
            } else {
                Tuple<Message, Connection> t = new
                        Tuple<Message, Connection>(msg, nextCon);
                return t;
            }
        }
        return null;
    }

    /**
     * Try to update router table and find the routing path from router table.
     * If 'msgPathLabel' is true, then the routing path should be written into the header.
     * @param message
     * @param connections
     * @param msgPathLabel
     * @return
     */
    public Tuple<Message, Connection> findPathFromRouterTabel(Message message) {
    	//update router table by using specific routing algorithm
        if (updateRouterTable(message) == false) {
            return null;
        }
        //get the routing path from router table
        List<Tuple<Integer, Boolean>> routerPath = this.routerTable.get(message.getTo());
        //System.out.println(this.getHost()+" send path "+routerPath+"  "+SimClock.getTime());
        //write the routing path into the header or not according to the 'msgPathLabel'
        if (msgPathLabel == true) {
            message.updateProperty(MSG_ROUTERPATH, routerPath);
        }
        
        Connection firstHop = findConnection(routerPath.get(0).getKey());
        if (firstHop != null) {
            Tuple<Message, Connection> t = new Tuple<Message, Connection>(message, firstHop);
            return t;
        } else {
            if (routerPath.get(0).getValue()) {
            	
                return null;
            } else {
            	this.routerTable.remove(message.getTo());
            	return null;
//                throw new SimError("No such connection: " + routerPath.get(0) +
//                       " at routerTable " + this);  
                
            }
        }
    }

    /**
     * Find the DTNHost according to its address
     *
     * @param address
     * @return
     */
    public DTNHost findHostByAddress(int address) {
        for (DTNHost host : getHosts()) {
            if (host.getAddress() == address)
                return host;
        }
        return null;
    }

    /**
     * Find the connection according to DTNHost's address
     * @param address
     * @return
     */
    public Connection findConnectionByAddress(int address) {
        for (Connection con : this.getHost().getConnections()) {
            if (con.getOtherNode(this.getHost()).getAddress() == address)
                return con;
        }
        return null;
    }

    /**
     * Update the router table
     *
     * @param msg
     * @return
     */
    public boolean updateRouterTable(Message msg) {
        shortestPathSearch(msg);
        //System.out.println(this.getHost()+"  "+routerTable+"  "+SimClock.getTime());
        if (this.routerTable.containsKey(msg.getTo())) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Return current network topology in forms of temporal graph
     */
    public HashMap<DTNHost, List<DTNHost>> temporalGraphCaluculation() {
        HashMap<DTNHost, Coord> locationRecord = new HashMap<DTNHost, Coord>();
        HashMap<DTNHost, List<DTNHost>> topologyInfo = new HashMap<DTNHost, List<DTNHost>>();

        double radius = transmitRange;//Represent communication Radius
        //Get satellite movement model which store orbit-info of all satellites in the network
        SatelliteMovement movementModel = ((SatelliteMovement) this.getHost().getMovementModel());

        //Calculate the current coordinate of all satellite nodes in the network
        for (DTNHost h : movementModel.getHosts()) {
            //locationRecord.put(h, movementModel.getCoordinate(h, SimClock.getTime()));
            locationRecord.put(h, h.getLocation());          
        }
        //System.out.println(this.getHost()+" list: "+locationRecord+"  "+SimClock.getTime());
        //Calculate links between each two satellite nodes
        for (DTNHost h : movementModel.getHosts()) {
            for (DTNHost otherNode : movementModel.getHosts()) {
                if (otherNode == h)
                    continue;
                Coord otherNodeLocation = locationRecord.get(otherNode);
                if (locationRecord.get(h).distance(otherNodeLocation) <= radius) {
                    if (topologyInfo.get(h) == null)
                        topologyInfo.put(h, new ArrayList<DTNHost>());
                    List<DTNHost> neighborList = topologyInfo.get(h);
                    if (neighborList == null) {
                        neighborList = new ArrayList<DTNHost>();
                        neighborList.add(otherNode);
                    } else {
                        neighborList.add(otherNode);
                    }
                }
            }
        }
        return topologyInfo;
    }

    /**
     * Core routing algorithm, utilizes greed approach to search the shortest path to the destination
     *
     * @param msg
     */
    public void shortestPathSearch(Message msg) {
        HashMap<DTNHost, List<DTNHost>> topologyInfo = temporalGraphCaluculation();//update the current topology information

        if (routerTableUpdateLabel == true)		//routerTableUpdateLabel == true�����˴θ���·�ɱ��Ѿ����¹��ˣ����Բ�Ҫ�ظ�����
            return;
        this.routerTable.clear();
        this.arrivalTime.clear();

        /**ȫ���Ĵ������ʼٶ�Ϊһ����**/
        double transmitSpeed = this.getHost().getInterface(1).getTransmitSpeed();
        /**��ʾ·�ɿ�ʼ��ʱ��**/

        /**�����·��̽�⵽��һ���ھ����񣬲�����·�ɱ�**/
        List<DTNHost> searchedSet = new ArrayList<DTNHost>();
        List<DTNHost> sourceSet = new ArrayList<DTNHost>();
        sourceSet.add(this.getHost());//��ʼʱֻ��Դ�ڵ���
        searchedSet.add(this.getHost());//��ʼʱֻ��Դ�ڵ�

        for (Connection con : this.getHost().getConnections()) {//�����·��̽�⵽��һ���ھӣ�������·�ɱ�
            DTNHost neiHost = con.getOtherNode(this.getHost());
            sourceSet.add(neiHost);//��ʼʱֻ�б��ڵ����·�ھ�
            Double time = getTime() + msg.getSize() / this.getHost().getInterface(1).getTransmitSpeed();
            List<Tuple<Integer, Boolean>> path = new ArrayList<Tuple<Integer, Boolean>>();
            Tuple<Integer, Boolean> hop = new Tuple<Integer, Boolean>(neiHost.getAddress(), false);
            path.add(hop);//ע��˳��
            arrivalTime.put(neiHost, time);
            routerTable.put(neiHost, path);
        }
        /**�����·��̽�⵽��һ���ھ����񣬲�����·�ɱ�**/

        int iteratorTimes = 0;
        int size = getHosts().size();
        boolean updateLabel = true;
        boolean predictLable = false;

        arrivalTime.put(this.getHost(), SimClock.getTime());//��ʼ������ʱ��

        /**���ȼ����У���������**/
        List<Tuple<DTNHost, Double>> PriorityQueue = new ArrayList<Tuple<DTNHost, Double>>();
        /**���ȼ����У���������**/
        
        while (true) {//Dijsktra�㷨˼�룬ÿ������ȫ�֣���ʱ����С�ļ���·�ɱ���֤·�ɱ�����Զ��ʱ����С��·��
            if (iteratorTimes >= size)//|| updateLabel == false)
                break;
            updateLabel = false;
            
            for (DTNHost c : sourceSet) {
                List<DTNHost> neiList = topologyInfo.get(c);//get neighbor nodes from topology info

                /**�ж��Ƿ��Ѿ�����������Դ���񼯺��е�����**/
                if (searchedSet.contains(c) || neiList == null)
                    continue;
                
                searchedSet.add(c);
                for (DTNHost eachNeighborNetgrid : neiList) {//startTime.keySet()���������е��ھӽڵ㣬����δ�����ھӽڵ�
                    if (sourceSet.contains(eachNeighborNetgrid))//ȷ������ͷ
                        continue;
                    
                    double time = arrivalTime.get(c) + msg.getSize() / transmitSpeed;
                    /**���·����Ϣ**/
                    List<Tuple<Integer, Boolean>> path = new ArrayList<Tuple<Integer, Boolean>>();
                    if (this.routerTable.containsKey(c))
                        path.addAll(this.routerTable.get(c));
                    Tuple<Integer, Boolean> thisHop = new Tuple<Integer, Boolean>(eachNeighborNetgrid.getAddress(), predictLable);
                    path.add(thisHop);//ע��˳��
                    /**���·����Ϣ**/
                    /**ά����С����ʱ��Ķ���**/
                    if (arrivalTime.containsKey(eachNeighborNetgrid)) {
                        /**���������Ƿ�����ͨ���������·��������У����ĸ�ʱ�����**/
                        if (time <= arrivalTime.get(eachNeighborNetgrid)) {
//                            if (random.nextBoolean() == true && time - arrivalTime.get(eachNeighborNetgrid) < 0.1) {//���ʱ����ȣ��������ѡ��
//
//                                /**ע�⣬�ڶԶ��н��е�����ʱ�򣬲��ܹ���forѭ������Դ˶��н����޸Ĳ���������ᱨ��**/
//                                int index = -1;
//                                for (Tuple<DTNHost, Double> t : PriorityQueue) {
//                                    if (t.getKey() == eachNeighborNetgrid) {
//                                        index = PriorityQueue.indexOf(t);
//                                    }
//                                }
//                                /**ע�⣬�������PriorityQueue���н��е�����ʱ�򣬲��ܹ���forѭ������Դ˶��н����޸Ĳ���������ᱨ��**/
//                                if (index > -1) {
//                                    PriorityQueue.remove(index);
//                                    PriorityQueue.add(new Tuple<DTNHost, Double>(eachNeighborNetgrid, time));
//                                    arrivalTime.put(eachNeighborNetgrid, time);
//                                    routerTable.put(eachNeighborNetgrid, path);
//                                }
//                            }

                            /**ע�⣬�ڶԶ��н��е�����ʱ�򣬲��ܹ���forѭ������Դ˶��н����޸Ĳ���������ᱨ��**/
                            int index = -1;
                            for (Tuple<DTNHost, Double> t : PriorityQueue) {
                                if (t.getKey() == eachNeighborNetgrid) {
                                    index = PriorityQueue.indexOf(t);
                                }
                            }
                            /**ע�⣬�������PriorityQueue���н��е�����ʱ�򣬲��ܹ���forѭ������Դ˶��н����޸Ĳ���������ᱨ��**/
                            if (index > -1) {
                                PriorityQueue.remove(index);
                                PriorityQueue.add(new Tuple<DTNHost, Double>(eachNeighborNetgrid, time));
                                arrivalTime.put(eachNeighborNetgrid, time);
                                routerTable.put(eachNeighborNetgrid, path);
                            }
                        }
                        /**���������Ƿ�����ͨ���������·��������У����ĸ�ʱ�����**/
                    } else {
                        PriorityQueue.add(new Tuple<DTNHost, Double>(eachNeighborNetgrid, time));
                        arrivalTime.put(eachNeighborNetgrid, time);
                        routerTable.put(eachNeighborNetgrid, path);
                    }
                    /**�Զ��н�������**/
                    sort(PriorityQueue);
                    updateLabel = true;
                }
            }
            iteratorTimes++;
            for (int i = 0; i < PriorityQueue.size(); i++) {
                if (!sourceSet.contains(PriorityQueue.get(i).getKey())) {
                    sourceSet.add(PriorityQueue.get(i).getKey());//���µ�����������
                    break;
                }
            }
//			if (routerTable.containsKey(msg.getTo()))//�����;�ҵ���Ҫ��·������ֱ���˳�����
//				break;
        }
        routerTableUpdateLabel = true;       
    }

    /**
     * Bubble sort algorithm 
     * @param distanceList
     * @return
     */
    public List<Tuple<DTNHost, Double>> sort(List<Tuple<DTNHost, Double>> distanceList) {
        for (int j = 0; j < distanceList.size(); j++) {
            for (int i = 0; i < distanceList.size() - j - 1; i++) {
                if (distanceList.get(i).getValue() > distanceList.get(i + 1).getValue()) {//��С���󣬴��ֵ���ڶ����Ҳ�
                    Tuple<DTNHost, Double> var1 = distanceList.get(i);
                    Tuple<DTNHost, Double> var2 = distanceList.get(i + 1);
                    distanceList.remove(i);
                    distanceList.remove(i);//ע�⣬һ��ִ��remove֮������List�Ĵ�С�ͱ��ˣ�����ԭ��i+1��λ�����ڱ����i
                    //ע��˳��
                    distanceList.add(i, var2);
                    distanceList.add(i + 1, var1);
                }
            }
        }
        return distanceList;
    }

    /**
     * Find the corresponding DTNHost from host address in 
     * routing path information (contained in message header)
     * @param path
     * @return
     */
    public List<DTNHost> getHostListFromPath(List<Integer> path) {
        List<DTNHost> hostsOfPath = new ArrayList<DTNHost>();
        for (int i = 0; i < path.size(); i++) {
            hostsOfPath.add(this.getHostFromAddress(path.get(i)));//���ݽڵ��ַ�ҵ�DTNHost
        }
        return hostsOfPath;
    }

    /**
     * Find the DTNHost according to its address
     * @param address
     * @return
     */
    public DTNHost getHostFromAddress(int address) {
        for (DTNHost host : getHosts()) {
            if (host.getAddress() == address)
                return host;
        }
        return null;
    }

    /**
     * Calculate the distance between two nodes.
     *
     * @param a
     * @param b
     * @return
     */
    public double getDistance(DTNHost a, DTNHost b) {
        double ax = a.getLocation().getX();
        double ay = a.getLocation().getY();
        double az = a.getLocation().getZ();
        double bx = a.getLocation().getX();
        double by = a.getLocation().getY();
        double bz = a.getLocation().getZ();

        double distance = (ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz);
        distance = Math.sqrt(distance);

        return distance;
    }

    /**
     * Find the specific connection according to neighbor node's address
     * @param address
     * @return
     */
    public Connection findConnection(int address) {
        List<Connection> connections = this.getHost().getConnections();
        for (Connection c : connections) {
            if (c.getOtherNode(this.getHost()).getAddress() == address) {
                return c;
            }
        }
        return null;
    }

    /**
     * Try to send the message through a specific connection
     * @param t
     * @return
     */

    public Message tryMessageToConnection(Tuple<Message, Connection> t) {
        if (t == null)
            throw new SimError("No such tuple: " + " at " + this);
        Message m = t.getKey();
        Connection con = t.getValue();
        int retVal = startTransfer(m, con);
        if (retVal == RCV_OK) {  	//accepted a message, don't try others
            return m;
        } else if (retVal > 0) { 	//ϵͳ���壬ֻ��TRY_LATER_BUSY����0����Ϊ1
            return null;          	// should try later -> don't bother trying others
        }
        return null;
    }

    /**
     * Judge the next hop is busy or not.
     *
     * @param t
     * @return
     */
    public boolean nextHopIsBusyOrNot(Tuple<Message, Connection> t) {

        Connection con = t.getValue();
        if (con == null)
        	return false;
        /**���������·��������������һ������·�Ѿ���ռ�ã�����Ҫ�ȴ�**/
        if (con.isTransferring() || ((ShortestPathFirstRouter) con.getOtherNode(this.getHost()).getRouter()).isTransferring()) {
            return true;//˵��Ŀ�Ľڵ���æ
        }
        return false;
        /**���ڼ�����е���·ռ������������ڵ��Ƿ��ڶ��ⷢ�͵��������update�������Ѿ������ˣ��ڴ������ظ����**/
    }

    /**
     * Try to send the message through a specific connection.
     *
     * @param t
     * @return
     */
    public boolean sendMsg(Tuple<Message, Connection> t) {
        if (t == null) {
            assert false : "error!";
            return false;
        } else {
        	// check the next hop is busy or not
            if (nextHopIsBusyOrNot(t) == true)
                return false;
            if (tryMessageToConnection(t) != null)
                return true;
            else
                return false;
        }
    }

    /**
     * Returns true if this router is transferring something at the moment or
     * some transfer has not been finalized.
     *
     * @return true if this router is transferring something
     */
    @Override
    public boolean isTransferring() {
        //�жϸýڵ��ܷ���д�����Ϣ�������������һ�����ϵģ�ֱ�ӷ��أ�������,�������ŵ��ѱ�ռ�ã�
        //����1�����ڵ��������⴫��
        if (this.sendingConnections.size() > 0) {//protected ArrayList<Connection> sendingConnections;
            return true; // sending something
        }

        List<Connection> connections = getConnections();
        //����2��û���ھӽڵ�
        if (connections.size() == 0) {
            return false; // not connected
        }
        //����3�����ھӽڵ㣬����������Χ�ڵ����ڴ���
        //ģ�������߹㲥��·�����ھӽڵ�֮��ͬʱֻ����һ�Խڵ㴫������!
        for (int i = 0, n = connections.size(); i < n; i++) {
            Connection con = connections.get(i);
            //isReadyForTransfer����false���ʾ���ŵ��ڱ�ռ�ã���˶��ڹ㲥�ŵ����Բ��ܴ���
            if (!con.isReadyForTransfer()) {
                return true;    // a connection isn't ready for new transfer
            }
        }
        return false;
    }

    /**
     * ����д������֤�ڴ������֮��Դ�ڵ����Ϣ��messages������ɾ��
     */
    @Override
    protected void transferDone(Connection con) {
        String msgId = con.getMessage().getId();
        if (msgId != null)
        	removeFromMessages(msgId);
    }

    /**
     * get all satellite nodes info in the movement model
     *
     * @return all satellite nodes in the network
     */
    public List<DTNHost> getHosts() {
        return new ArrayList<DTNHost>(((SatelliteMovement) this.getHost().getMovementModel()).getHosts());
    }
}
