/*
 * Copyright 2016 University of Science and Technology of China , Infonet Lab
 * Written by LiJian.
 */
package movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import satellite_orbit.SatelliteOrbit;
import core.Coord;
import core.DTNHost;
import core.Settings;
import core.SimError;

public class SatelliteMovement extends MovementModel {
    /**
     * six orbit parameters
     */
    private double a = 8000.; // sma in km
    private double e = 0.1; // eccentricity
    private double i = 15; // inclination in degrees
    private double raan = 0.0; // right ascension of ascending node in degrees
    private double w = 0.0; // argument of perigee in degrees
    private double ta = 0.0; // true anomaly in degrees
    /**
     * record orbit-info of all satellite nodes in the network
     */
    private HashMap<DTNHost, double[]> orbitInfo;
    /** to create the entity of satellite orbit calculation model */
    private SatelliteOrbit satelliteOrbit;
    /** total LEO satellites */
    private int LEOtotalSatellites;
    /** total LEO orbit planes */
    private int LEOtotalPlane;
    /** number of orbit plane that this satellite belongs */
    private int nrofPlane;
    /** indicate which satellite in its orbit plane */
    private int nrofSatelliteINPlane;
    /** all satellite nodes in the network */
    private List<DTNHost> hosts = new ArrayList<DTNHost>();
    /** indicates satellite type: LEO, MEO or GEO */
    private String satelliteType;
    
    public SatelliteMovement(Settings settings) {
        super(settings);
    }

    protected SatelliteMovement(SatelliteMovement rwp) {
        super(rwp);
    }

    /**
     * set all satellite hosts list
     *
     * @param hosts
     */
    public void setHostsList(List<DTNHost> hosts) {
        this.hosts = hosts;
    }

    /**
     * set orbit during the initialization
     *
     * @param LEOtotalSatellites
     * @param LEOtotalPlane
     * @param nrofPlane
     * @param nrofSatelliteInPlane
     */
    public void setOrbit(int LEOtotalSatellites, int LEOtotalPlane, int nrofPlane, int nrofSatelliteInPlane) {
    	if (LEOtotalSatellites <= 0 || LEOtotalPlane <= 0)
    		throw new SimError("Setting Paramater Error");
        this.LEOtotalSatellites = LEOtotalSatellites;// total LEO satellites
        this.LEOtotalPlane = LEOtotalPlane;// total LEO orbit planes   
        this.nrofPlane = nrofPlane;// number of orbit plane that this satellite belongs
        this.nrofSatelliteINPlane = nrofSatelliteInPlane;// indicate which satellite in its orbit plane
    }
    /**
     * @return total LEO orbit plane numbers
     */
    public int getTotalNrofLEOPlanes(){
    	return LEOtotalPlane;// total LEO orbit planes
    }
    /**
     * @return total LEO satellite numbers
     */
    public int getTotalNrofLEOSatellites(){
    	return LEOtotalSatellites;
    }
    /**
     * set six orbit parameters
     *
     * @param parameters
     */
    public void setOrbitParameters(double[] parameters) {
        assert parameters.length >= 6 : "orbit parameters initialization error";
        
        this.a = parameters[0]; // sma in km
        this.e = parameters[1]; // eccentricity
        this.i = parameters[2]; // inclination in degrees
        this.raan = parameters[3]; // right ascension of ascending node in degrees
        this.w = parameters[4]; // argument of perigee in degrees
        this.ta = parameters[5]; // true anomaly in degrees

        double[] orbitParameters = new double[6];
        for (int j = 0; j < 6; j++) {
            orbitParameters[j] = parameters[j];
        }
        
        if (parameters.length > 6){
            //set satellite type, i.e., LEO,MEO or GEO
            switch((int)parameters[6]){
            	case 1:{
            		satelliteType = "LEO";
            		break;
            	}
            	case 2:{
            		satelliteType = "MEO";
            		break;
            	}
            }
        }

        
        this.satelliteOrbit = new SatelliteOrbit(orbitParameters);
    }

    /**
     * get satellite coordinate in specific time
     *
     * @param time
     * @return
     */
    public double[] getSatelliteCoordinate(double time) {
        double[][] coordinate = new double[1][3];
        double[] xyz = new double[3];

        Settings s = new Settings("MovementModel");
        int worldSize[] = s.getCsvInts("worldSize");

        coordinate = satelliteOrbit.getSatelliteCoordinate(time);
        /**ONE中的距离单位为meter，但是JAT中的轨道半径单位为km，因此在得到的坐标中应该*1000进行转换**/
//		xyz[0] = (coordinate[0][0]*1000 + worldSize/2);//坐標軸平移
//		xyz[1] = (coordinate[0][1]*1000 + worldSize/2);
//		xyz[2] = (coordinate[0][2]*1000 + worldSize/2);
        /**ONE中的距离单位为meter，但是JAT中的轨道半径单位为km，因此此做统一缩放，将ONE中的距离单位也视作km，同时坐标平移量保持为world大小的一半**/
        xyz[0] = (coordinate[0][0] + worldSize[0] / 2);// move the coordinate axis
        xyz[1] = (coordinate[0][1] + worldSize[0] / 2);
        xyz[2] = (coordinate[0][2] + worldSize[0] / 2);

        return xyz;
    }

    /**
     * calculate the orbit coordinate according to the orbit parameters
     *
     * @param parameters
     * @param time
     * @return
     */
    public double[] calculateOrbitCoordinate(double[] parameters, double time) {
        double[][] coordinate = new double[1][3];
        double[] xyz = new double[3];
        SatelliteOrbit so = new SatelliteOrbit(parameters);
        coordinate = so.getSatelliteCoordinate(time);
        
        Settings s = new Settings("MovementModel");
        int worldSize[] = s.getCsvInts("worldSize");
        /**ONE中的距离单位为meter，但是JAT中的轨道半径单位为km，因此此做统一缩放，将ONE中的距离单位也视作km，同时坐标平移量保持为world大小的一半**/
        xyz[0] = (coordinate[0][0] + worldSize[0] / 2);// move the coordinate axis
        xyz[1] = (coordinate[0][1] + worldSize[0] / 2);
        xyz[2] = (coordinate[0][2] + worldSize[0] / 2);

        return xyz;
    }

    /**
     * @return number of orbit plane
     */
    public int getNrofPlane() {
        return this.nrofPlane;
    }

    /**
     * @return number of satellite in its orbit plane
     */
    public int getNrofSatelliteINPlane() {
        return this.nrofSatelliteINPlane;
    }

    /**
     * 
     * @return orbit period
     */
    public double getPeriod(){
    	return this.satelliteOrbit.getPeriod();
    }
    /**
     * Returns a possible (random) placement for a host
     *
     * @return Random position on the map
     */
    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";
        Coord c = randomCoord();

        return c;
    }

    @Override
    public Path getPath() {
        Path p;
        p = new Path(generateSpeed());

        return p;
    }

    @Override
    public SatelliteMovement replicate() {
        return new SatelliteMovement(this);
    }

    protected Coord randomCoord() {
        return new Coord(rng.nextDouble() * getMaxX(),
                rng.nextDouble() * getMaxY());
    }
    /**
     * Initialization entrance, set orbit-info during the initialization
     *
     * @param orbitInfo
     * @param hosts
     */
    public void setOrbitInfo(HashMap<DTNHost, double[]> orbitInfo, List<DTNHost> hosts) {
        this.orbitInfo = orbitInfo;
        this.hosts = hosts;
        //Initialize orbit parameters of this satellite host
        setOrbitParameters(orbitInfo.get(this.getHost()));
        //set all satellite hosts list
        setHostsList(new ArrayList<DTNHost>(orbitInfo.keySet()));
    }

    /**
     * @return all orbit-info of all satellite nodes in the network
     */
    public HashMap<DTNHost, double[]> getOrbitInfo() {
        return this.orbitInfo;
    }

    /**
     * @return all satellite nodes set
     */
    public Set<DTNHost> getHosts() {
        return orbitInfo.keySet();
    }

    /**
     * @param host
     * @param time
     * @return a satellite's location in specific time
     */
    public Coord getCoordinate(DTNHost host, double time) {
        double[] p = orbitInfo.get(host);
        double[] location = calculateOrbitCoordinate(p, time);

        return new Coord(location[0], location[1], location[2]);
    }
    /**
     * @return satellite type
     */
    public String getSatelliteType(){
    	return satelliteType;
    }
}
