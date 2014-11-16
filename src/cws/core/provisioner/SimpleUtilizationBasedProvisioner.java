package cws.core.provisioner;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import cws.core.Provisioner;
import cws.core.VM;
import cws.core.WorkflowEngine;
import cws.core.WorkflowEvent;
import cws.core.cloudsim.CloudSimWrapper;
import cws.core.core.VMType;

public class SimpleUtilizationBasedProvisioner extends CloudAwareProvisioner implements Provisioner {

    // above this utilization threshold we start provisioning additional VMs
    private static final double UPPER_THRESHOLD = 0.90;
    // below this utilization threshold we start deprovisioning vms
    private static final double LOWER_THRESHOLD = 0.70;
    private int initialNumVMs = 0;

    public SimpleUtilizationBasedProvisioner(double maxScaling, CloudSimWrapper cloudsim) {
        super(maxScaling, cloudsim);
    }

    @Override
    public void provisionResources(WorkflowEngine engine) {
        // when called for the first time it should obtain the initial number of VMs
        if (initialNumVMs == 0) {
            initialNumVMs = engine.getAvailableVMs().size();
            if (initialNumVMs == 0) {// send event to initiate next provisioning cycle
                // We need to wait after initial VMs are created.
                getCloudsim().send(engine.getId(), engine.getId(), PROVISIONER_INTERVAL,
                        WorkflowEvent.PROVISIONING_REQUEST, null);
                return;
            }
        }

        // check the deadline and budget constraints

        double budget = engine.getBudget();
        double deadline = engine.getDeadline();
        double time = getCloudsim().clock();
        double cost = engine.getCost();

        // assuming all VMs are homogeneous
        double vmPrice = environment.getSingleVMPrice();

        // running vms are free + busy
        Set<VM> runningVMs = new HashSet<VM>(engine.getFreeVMs());
        runningVMs.addAll(engine.getBusyVMs());

        int numVMsRunning = runningVMs.size();

        // find VMs that will complete their billing unit
        // during the next provisioning cycle
        Set<VM> completingVMs = new HashSet<VM>();

        for (VM vm : runningVMs) {
            double vmRuntime = vm.getRuntime();

            // full billing units (rounded up)
            double vmBillingUnits = Math.ceil(vmRuntime / environment.getBillingTimeInSeconds());

            // seconds till next full unit
            double secondsRemaining = vmBillingUnits * environment.getBillingTimeInSeconds() - vmRuntime;

            // we add delay estimate to include also the deprovisioning time
            if (secondsRemaining <= environment.getDeprovisioningDelayEstimation() + PROVISIONER_INTERVAL) {
                completingVMs.add(vm);
            }
        }

        int numVMsCompleting = completingVMs.size();

        // if we are close to the budget
        if (budget - cost < vmPrice * numVMsCompleting || time + environment.getDeprovisioningDelayEstimation() + PROVISIONER_INTERVAL >= deadline) {

            // compute number of vms to terminate
            // it is the number that would overrun the budget if not terminated
            int numToTerminate = numVMsRunning - (int) Math.floor(((budget - cost) / vmPrice));

            // even if we have some budget left we should terminate all the instances past the deadline.
            if (time + environment.getDeprovisioningDelayEstimation() + PROVISIONER_INTERVAL >= deadline)
                numToTerminate = numVMsRunning;

            if (numToTerminate > numVMsRunning) {
                numToTerminate = numVMsRunning;
            }

            getCloudsim().log(
                    "Provisioner: number of instances to terminate: " + numToTerminate + ", numVMsCompleting: "
                            + numVMsCompleting + ", numVMsRunning: " + numVMsRunning);

            // set of vms scheduled for termination
            Set<VM> toTerminate = new HashSet<VM>();

            // select VMs to terminate
            if (numToTerminate < numVMsCompleting) {
                // select only from completing vms
                Iterator<VM> completingIt = completingVMs.iterator();
                for (int i = 0; i < numToTerminate; i++) {
                    VM vm;
                    vm = completingIt.next();
                    toTerminate.add(vm);
                }
            } else {
                // terminate all completing and add more from free and busy ones
                toTerminate.addAll(completingVMs);
                int added = toTerminate.size();

                Iterator<VM> freeIt = engine.getFreeVMs().iterator();
                Iterator<VM> busyIt = engine.getBusyVMs().iterator();
                while (added < numToTerminate) {
                    VM vm;
                    if (freeIt.hasNext())
                        vm = freeIt.next();
                    else
                        vm = busyIt.next();
                    if (toTerminate.add(vm))
                        added++;
                }
                Iterator<VM> allVmsIt = getCloud().getAllVms().iterator();
                while (added < numToTerminate && allVmsIt.hasNext()) {
                    VM vm = allVmsIt.next();
                    if (!toTerminate.contains(vm)) {
                        toTerminate.add(vm);
                    }
                }
            }

            // start terminating vms
            Set<VM> terminated = terminateInstances(engine, toTerminate);
            // remove terminated vms from free and busy sets
            engine.getFreeVMs().removeAll(terminated);
            engine.getBusyVMs().removeAll(terminated);

            // some instances may be still running so we want to be invoked again to stop them before they reach full
            // billing unit
            if (getCloud().getAllVms().size() > 0)
                getCloudsim().send(engine.getId(), engine.getId(), PROVISIONER_INTERVAL,
                        WorkflowEvent.PROVISIONING_REQUEST, null);
            // return without further provisioning
            return;
        }

        // compute utilization
        if (getCloud().getAllVms().size() == 0) {
            // No machines - finish.
            return;
        }
        double utilization = engine.getBusyVMs().size() / (getCloud().getAllVms().size());

        if (!(utilization >= 0.0)) {
            getCloudsim().log(
                    "Provisioner: utilization: " + utilization + ", budget consumed: " + cost
                            + ", number of instances: " + numVMsRunning + ", number of instances completing: "
                            + numVMsCompleting + ", cost: " + cost + ", budget:" + budget);
            throw new RuntimeException("Utilization is not >= 0.0");
        }
        
        // if we are close to constraints we should not provision new vms
        boolean finishing_phase = budget - cost <= vmPrice * numVMsRunning || time + environment.getDeprovisioningDelayEstimation() + PROVISIONER_INTERVAL >= deadline;

        // if:
        // we are not in finishing phase,
        // and utilization is high
        // and we are below max limit
        // and we have money left for one instance more
        // then: deploy new instance
        double provisioning_interval = PROVISIONER_INTERVAL;
        if (!finishing_phase && utilization > UPPER_THRESHOLD
                && getCloud().getAllVms().size() < getMaxScaling() * initialNumVMs && budget - cost >= vmPrice) {

            // TODO(mequrel): should be extracted, the best would be to have an interface createVM available
            VMType vmType = environment.getVMType();
            VM vm = new VM(vmType, getCloudsim());

            getCloudsim().log("Starting VM: " + vm.getId());
            getCloudsim().send(engine.getId(), getCloud().getId(), 0.0, WorkflowEvent.VM_LAUNCH, vm);
            provisioning_interval = 0;
        } else if (!finishing_phase && utilization < LOWER_THRESHOLD) {
            // select Vms to terminate
            Set<VM> toTerminate = new HashSet<VM>();

            // terminate half of the instances
            // make sure that if there is only one instance it should be terminated
            int numToTerminate = (int) Math.ceil(numVMsCompleting / 2.0);
            // Do not terminate too many machines. I.e. so that we will get over upper threshold.
            while (getCloud().getAllVms().size() - numToTerminate != 0
                    && (engine.getBusyVMs().size() / (getCloud().getAllVms().size() - numToTerminate)) > UPPER_THRESHOLD
                    && numToTerminate > 0) {
                numToTerminate--;
            }
            if (numToTerminate > 0) {
                Iterator<VM> vmIt = completingVMs.iterator();
                for (int i = 0; i < numToTerminate && vmIt.hasNext(); i++) {
                    toTerminate.add(vmIt.next());
                }

                Set<VM> terminated = terminateInstances(engine, toTerminate);
                engine.getFreeVMs().removeAll(terminated);
                engine.getBusyVMs().removeAll(terminated);
                provisioning_interval = 0;
            }
        }
        // send event to initiate next provisioning cycle
        getCloudsim().send(engine.getId(), engine.getId(), provisioning_interval, WorkflowEvent.PROVISIONING_REQUEST,
                null);
    }

    /**
     * This method terminates instances but only the ones
     * that are close to the full billing unit of operation.
     * Thus this method has to be invoked several times
     * to effectively terminate all the instances.
     * The method modifies the given vmSet by removing the terminated Vms.
     * 
     * @param engine
     * @param vmSet
     * @return set of VMs that were terminated
     */
    private Set<VM> terminateInstances(WorkflowEngine engine, Set<VM> vmSet) {
        Set<VM> removed = new HashSet<VM>();
        Iterator<VM> vmIt = vmSet.iterator();

        while (vmIt.hasNext()) {
            VM vm = vmIt.next();
            vmIt.remove();
            removed.add(vm);
            getCloudsim().send(engine.getId(), getCloud().getId(), 0.0, WorkflowEvent.VM_TERMINATE, vm);
        }
        return removed;
    }
}
