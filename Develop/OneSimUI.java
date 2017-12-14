/* 
 * Copyright 2016 University of Science and Technology of China , Infonet
 * 
 */
package Develop;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import com.sun.j3d.utils.applet.MainFrame;

import ui.DTNSimTextUI;
import core.DTN2Manager;
import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.SimScenario;

import java.awt.*;

public class OneSimUI extends DTNSimTextUI{
	private long lastUpdateRt;									// real time of last ui update
	private long startTime; 									// simulation start time
	private  EventLog eventLog;
	/** namespace of scenario settings ({@value})*/
	public static final String SCENARIO_NS = "Scenario";
	/** end time -setting id ({@value})*/
	public static final String END_TIME_S = "endTime";
	/** update interval -setting id ({@value})*/
	public static final String UP_INT_S = "updateInterval";
	/** How often the UI view is updated (milliseconds) */     
	public static final long UI_UP_INTERVAL = 60000;
	/** List of hosts in this simulation */
	protected List<DTNHost> hosts;
	
	public Main_Window main;
	InfoPanel infoPanel;
	/**
	 * Initializes the simulator model.
	 */
	private void NewWindow() {
		/**��ʼ��ͼ�ν���*/
		//this.eventLog = new EventLog(this);
		//this.hosts = this.scen.getHosts();
		this.infoPanel = new InfoPanel(this);
		main = new Main_Window(this.infoPanel);//eventLog,hosts);
		
		//scen.addMessageListener(eventLog);
		//scen.addConnectionListener(eventLog);
		
		main.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		main.setLocationRelativeTo(null);
		main.setVisible(true);	
	}

	/**
	 * Starts the simulation.
	 */
	public void start() {
		startGUI();
		
		while(true){
			while(main.getPaused() == true){			// ����ȴ�ȷ�����ò���
				try {
					 synchronized (this){
						wait(10);
					 }
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			/**���ó�ʼ�����ã����֮ǰ�ĳ�ʼ��ʵ�壬���������ʼ����Ч**/
			/**���г�ʼ��**/
			WaitingWindow t = this.Loading();			// ���ڼ���ʱ��
			super.initModel();
			setUI();		
			t.dispose();
			
			runSim();
			main.setPaused(false);//һ�η�������֮����ϵͳ������ͣ״̬������ѭ����ʼ��һ�ַ���
			main.parameter.setEnabled(true);//��������༭���ý���
		}
	}
	/**
	 * ��UIȷ�����ò�������ɳ�ʼ��֮��ˢ��UI��ʾ�������¼����ڣ��ڵ��б��Լ�3Dͼ����ʾ
	 */
	private void setUI(){
		main.setNodeList(this.scen.getHosts());//ˢ�½ڵ��б���ʾ
		resetEvenetLog();
		reset3DWindow();
		main.resetSimCancelled();	//����SimCancelled��ֵ
		main.parameter.setEnabled(false);
	}
	/**
	 * ˢ��UI�е��¼�����
	 */
	private void resetEvenetLog(){
		this.eventLog = new EventLog(this);//���ʱ�䴰��
	    eventLog.setBorder(new TitledBorder("�¼�����"));
	    main.resetEventLog(eventLog);
		scen.addMessageListener(eventLog);
		scen.addConnectionListener(eventLog);
	}
	/**
	 * ˢ��UI�е�3Dͼ�δ���
	 */
	private void reset3DWindow(){
		//this.hosts = this.scen.getHosts();
		main.set3DWindow();//�ڳ�ʼ��֮���ٵ���3D����
	    main.items[2].setEnabled(true);
	    main.items[6].setEnabled(true);//���濪ʼʱ������3D��2D������ʾ��ťΪ����
	}
	/**
	 * ����GUI����
	 */
	private void startGUI() {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
			    public void run() {
					try {
						NewWindow();
					} catch (AssertionError e) {
						processAssertionError(e);
					}
			    }
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
	}
	
	protected void processAssertionError(AssertionError e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void runSim(){
		Settings s = new Settings(SCENARIO_NS);
			
		while(main.getPaused() == true){			// ����ȴ�ȷ�����ò���
			try {
				 synchronized (this){
					wait(10);
				 }
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		this.setParameter();
		double simTime = SimClock.getTime();
		double endTime = scen.getEndTime();
		
		// ----------------------- ���ڲ��Բ��� --------------------------------//
		System.out.println("����ʱ��"+"  "+endTime);
		System.out.println("����ʱ�䣺"+"  "+scen.getUpdateInterval());
		// ----------------------- ���ڲ��Բ��� --------------------------------//
		
		
		startTime = System.currentTimeMillis();
		lastUpdateRt = startTime;
		
		DTN2Manager.setup(world);
		while (simTime < endTime && !main.getSimCancelled()){			
			if (main.getPaused()) {
				try {
					 synchronized (this){
						wait(10);
					 }
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else{
				try {
					world.update();
					this.updateTime();   					//���ڸ��·���ʱ��
				} catch (AssertionError e) {
					e.printStackTrace();
					done();
					return;
				}
				simTime = SimClock.getTime();
			}
			this.update(false);
		}
		
		double duration = (System.currentTimeMillis() - startTime)/1000.0;
		
		simDone = true;
		done();
		this.update(true); // force final UI update
		
		print("Simulation done in " + String.format("%.2f", duration) + "s");
	}
	/**
	 * Updates user interface if the long enough (real)time (update interval)
	 * has passed from the previous update.
	 * @param forced If true, the update is done even if the next update
	 * interval hasn't been reached.
	 */
	private void update(boolean forced) {
		long now = System.currentTimeMillis();
		long diff = now - this.lastUpdateRt;
		double dur = (now - startTime)/1000.0;
		if (forced || (diff > UI_UP_INTERVAL)) {
			// simulated seconds/second 
			double ssps = ((SimClock.getTime() - lastUpdate)*1000) / diff;
			this.lastUpdateRt = System.currentTimeMillis();
			this.lastUpdate = SimClock.getTime();
		}		
	}
	
	private void print(String txt) {
		System.out.println(txt);
	}
	
	/**
	 * ���ӽ����������ò���֮�󣬽���������д�뵽scen�У�������Ӧ������������ԭ�г����ȡ��
	 */
	private void setParameter(){
		Settings s = new Settings(SCENARIO_NS);
		double interval =  s.getDouble(UP_INT_S);	//	����ʱ��
		scen.setUpdateInterval(interval);
		System.out.println(interval);
		
		double endTime = s.getDouble(END_TIME_S);	//	����ʱ��
		scen.setEndTime(endTime);
	}
	
    /**
     * Sets a node's graphical presentation in the center of the playfield view
     * @param host The node to center
     */
    public void setFocus(DTNHost host) {
    	//centerViewAt(host.getLocation());
    	infoPanel.showInfo(host);
    	//showPath(host.getPath()); // show path on the playfield
    }
    /**
     * Returns the info panel of the GUI
     * @return the info panel of the GUI
     */
    public InfoPanel getInfoPanel() {
    	return this.infoPanel;
    }
    /**
     * ���·���ʱ��
     */
    private void updateTime() {
    	double simTime = SimClock.getTime();
    	this.lastUpdate = simTime;
    	main.setSimTime(simTime); //update time to control panel
    }
    /**
     * ������Դ
     */
    private WaitingWindow Loading(){
		WaitingWindow t = new WaitingWindow();
		new Thread(t).start();
		//t.run();
		return t;
    }
    
}
