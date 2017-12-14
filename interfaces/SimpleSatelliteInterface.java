/*
 * Copyright 2016 University of Science and Technology of China , Infonet Lab
 * Written by LiJian.
 */
package interfaces;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import routing.OptimizedClusteringRouter;
import core.CBRConnection;
import core.Connection;
import core.NetworkInterface;
import core.Settings;
import core.DTNHost;
import core.SimError;

/**
 * A simple Network Interface that provides a constant bit-rate service, where
 * one transmission can be on at a time.
 */
public class SimpleSatelliteInterface extends NetworkInterface {
	
	/** router mode in the sim -setting id ({@value})*/
	public static final String USERSETTINGNAME_S = "userSetting";
	/** router mode in the sim -setting id ({@value})*/
	public static final String ROUTERMODENAME_S = "routerMode";
	public static final String DIJSKTRA_S = "dijsktra";
	public static final String SIMPLECONNECTIVITY_S = "simpleConnectivity";

	private Collection<NetworkInterface> interfaces;
	
	/** indicates the interface type, i.e., radio or laser*/
	public static final String interfaceType = "RadioInterface";
	
	/**
	 * Reads the interface settings from the Settings file
	 */
	public SimpleSatelliteInterface(Settings s)	{
		super(s);
	}
		
	/**
	 * Copy constructor
	 * @param ni the copied network interface object
	 */
	public SimpleSatelliteInterface(SimpleSatelliteInterface ni) {
		super(ni);
	}

	public NetworkInterface replicate()	{
		return new SimpleSatelliteInterface(this);
	}

	/**
	 * Tries to connect this host to another host. The other host must be
	 * active and within range of this host for the connection to succeed. 
	 * @param anotherInterface The interface to connect to
	 */
	public void connect(NetworkInterface anotherInterface) {
		if (isScanning()  
				&& anotherInterface.getHost().isRadioActive() 
				&& isWithinRange(anotherInterface) 
				&& !isConnected(anotherInterface)
				&& (this != anotherInterface)) {
			// new contact within range
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();//�������˵����������ɽ�С��һ������
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);//���������˫����host�ڵ㣬����������ɵ�����con���������б���
		}
	}

	/**
	 * Independent calculation process in each node, which is used in multi-thread method.
	 */
	public Collection<NetworkInterface> multiThreadUpdate(){
		if (optimizer == null) {
			return null; /* nothing to do */
		}

		// First break the old ones
		optimizer.updateLocation(this);

		this.interfaces =
				optimizer.getNearInterfaces(this);
		return interfaces;
	}
	/**
	 * Updates the state of current connections (i.e. tears down connections
	 * that are out of range and creates new ones).
	 */
	public void update() {
	
		if (!this.getHost().multiThread){
			if (optimizer == null) {
				return; /* nothing to do */
			}
			
			// First break the old ones
			optimizer.updateLocation(this);
		}

		for (int i=0; i<this.connections.size(); ) {
			Connection con = this.connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);

			// all connections should be up at this stage
			assert con.isUp() : "Connection " + con + " was down!";

			if (!isWithinRange(anotherInterface)) {//���½ڵ�λ�ú󣬼��֮ǰά���������Ƿ����Ϊ̫Զ���ϵ�
				disconnect(con,anotherInterface);
				connections.remove(i);
			}
			else {
					i++;
			}
		}
		Settings s = new Settings(USERSETTINGNAME_S);
		String mode = s.getSetting(ROUTERMODENAME_S);
		
		switch (mode) { 
		case "AllConnected":{
			if (!this.getHost().multiThread) {
				// Then find new possible connections
				interfaces = optimizer.getNearInterfaces(this);
			}
			for (NetworkInterface i : interfaces) {
				connect(i);
			}
			break;
		}
		case "Cluster":{
			if (!this.getHost().multiThread) {
				// Then find new possible connections
				interfaces = optimizer.getNearInterfaces(this);
			}
			
			/**����ڷ�Χ�ڵ�����ڵ�Ȳ���ͬһƽ���ڵģ��ֲ���ͨѶ�ڵ㣬�Ͳ��������ӣ���ʡ����**/
			
			if (allowToConnectNodesInLEOPlane == null){
				throw new SimError("��ʼ��û�м���ÿ�����ƽ���ڵĽڵ㣬������NetworkInterface���У�");
			}
			for (NetworkInterface i : interfaces) {	
				boolean allowConnection = true;
				switch(i.getHost().getSatelliteType()){
					case "LEO":{						
						if (!allowToConnectNodesInLEOPlane.contains(i.getHost()))
							allowConnection = false;
						break;
					}
					case "MEO":{
						break;
					}
				}
				
				if (allowConnection){//������λ���Ž�������
					connect(i);
				}
			}
			break;
		}
		/*
		 * case 3://�ִ�ģʽ Collection<NetworkInterface> interfaces_
		 * =//�������optimizer
		 * .getNearInterfaces(this)����ȡ�ھӽڵ��ˣ��������ӵĽ���ȫ������world��java���н���
		 * optimizer.getNearInterfaces(this, clusterHosts, hostsOfGEO); for
		 * (NetworkInterface i : interfaces_) { connect(i); } break;
		 */
		}

	}

	
	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * @param anotherInterface The interface to create the connection to
	 */
	public void createConnection(NetworkInterface anotherInterface) {
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {    			
			// connection speed is the lower one of the two speeds 
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed; 
			}

			Connection con = new CBRConnection(this.host, this, 
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con,anotherInterface);
		}
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "SatelliteLaserInterface " + super.toString();
	}

}
