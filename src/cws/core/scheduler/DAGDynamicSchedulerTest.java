package cws.core.scheduler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.junit.Test;

import cws.core.Cloud;
import cws.core.DAGJob;
import cws.core.EnsembleManager;
import cws.core.Job;
import cws.core.Provisioner;
import cws.core.Scheduler;
import cws.core.VM;
import cws.core.WorkflowEngine;
import cws.core.WorkflowEvent;
import cws.core.dag.DAG;
import cws.core.dag.DAGParser;
import cws.core.dag.Task;
import cws.core.log.WorkflowLog;


public class DAGDynamicSchedulerTest implements WorkflowEvent {

	@Test
	public void testScheduleVMS() {
		CloudSim.init(1, null, false);

		
		Provisioner provisioner = null;
		DAGDynamicScheduler scheduler = new DAGDynamicScheduler();
		WorkflowEngine engine = new WorkflowEngine(provisioner , scheduler);
		Cloud cloud = new Cloud();
		

		
		HashSet<VM> vms = new HashSet<VM>();
		for (int i = 0; i < 10; i++) {
			VM vm = new VM(1000, 1, 1.0, 1.0);
			vms.add(vm);
			CloudSim.send(engine.getId(), cloud.getId(), 0.1, VM_LAUNCH, vm);
		}

		CloudSim.startSimulation();

		assertEquals(vms.size(), engine.getAvailableVMs().size());

	}
	
	
	
	@Test
	public void testScheduleDag() {
		
		
		CloudSim.init(1, null, false);

		
		Provisioner provisioner = null;
		DAGDynamicScheduler scheduler = new DAGDynamicScheduler();
		WorkflowEngine engine = new WorkflowEngine(provisioner , scheduler);
		Cloud cloud = new Cloud();
		
		
		WorkflowLog jobLog = new WorkflowLog();
		engine.addJobListener(jobLog);
		
		HashSet<VM> vms = new HashSet<VM>();
		for (int i = 0; i < 10; i++) {
			VM vm = new VM(1000, 1, 1.0, 1.0);
			vms.add(vm);
			CloudSim.send(engine.getId(), cloud.getId(), 0.0, VM_LAUNCH, vm);
		}
		
		DAG dag = new DAG();
		for (int i = 0; i < 100; i++) {
			Task task = new Task("TASK" + i, "transformation", (i%10));
			dag.addTask(task);
		}

		List<DAG> dags = new ArrayList<DAG>();
		dags.add(dag);
		
		EnsembleManager em = new EnsembleManager(dags, engine);

		
		CloudSim.startSimulation();

		assertEquals(vms.size(), engine.getAvailableVMs().size());
		assertEquals(0, engine.getQueuedJobs().size());
		
		jobLog.printJobs("testDynamicSchedulerDag");
	}
	
	@Test
	public void testScheduleDag100() {
		
		
		CloudSim.init(1, null, false);

		
		Provisioner provisioner = null;
		DAGDynamicScheduler scheduler = new DAGDynamicScheduler();
		WorkflowEngine engine = new WorkflowEngine(provisioner , scheduler);
		Cloud cloud = new Cloud();
		

		WorkflowLog jobLog = new WorkflowLog();		
		engine.addJobListener(jobLog);
		
		HashSet<VM> vms = new HashSet<VM>();
		for (int i = 0; i < 10; i++) {
			VM vm = new VM(1000, 1, 1.0, 1.0);
			vms.add(vm);
			CloudSim.send(engine.getId(), cloud.getId(), 0.0, VM_LAUNCH, vm);
		}
		
		
		DAG dag = DAGParser.parseDAG(new File("dags/CyberShake_100.dag"));
		
		
		List<DAG> dags = new ArrayList<DAG>();
		dags.add(dag);
		
		EnsembleManager em = new EnsembleManager(dags, engine);

		
		CloudSim.startSimulation();

		assertEquals(vms.size(), engine.getAvailableVMs().size());
		assertEquals(0, engine.getQueuedJobs().size());
		
		jobLog.printJobs("testDynamicSchedulerDag_CyberShake_100");
	}

}