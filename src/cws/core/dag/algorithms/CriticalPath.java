package cws.core.dag.algorithms;

import java.util.HashMap;
import java.util.Map;

import cws.core.dag.Task;
import cws.core.core.VMType;

/**
 * Compute longest path using topological order,
 * http://en.wikipedia.org/wiki/Longest_path_problem#Weighted_directed_acyclic_graphs
 * @author malawski
 */
public class CriticalPath {
    private final Map<Task, Double> earliestFinishTimes = new HashMap<Task, Double>();

    public CriticalPath(TopologicalOrder order, VMType vmType) {
        this(order, null, vmType);
    }

    public CriticalPath(TopologicalOrder order, Map<Task, Double> runtimes,
                        VMType vmType) {
        if (runtimes == null) {
            runtimes = new HashMap<Task, Double>();
            for (Task task : order) {
                runtimes.put(task, getPredictedTaskRuntime(task, vmType));
            }
        }

        // Initially the finish time is whatever the runtime is
        for (Task task : order) {
            earliestFinishTimes.put(task, runtimes.get(task));
        }

        // Now we adjust the values in the topological order
        for (Task task : order) {
            for (Task child : task.getChildren()) {
                earliestFinishTimes.put
                    (child, Math.max(earliestFinishTimes.get(child),
                                     earliestFinishTimes.get(task) + runtimes.get(child)));
            }
        }
    }

    /**
     * Estimates and returns predicted task's runtime. May be overridden by subclasses to return estimations based on
     * different criteria.
     */
    protected double getPredictedTaskRuntime(Task task, VMType vmType) {
        return vmType.getPredictedTaskRuntime(task);
    }

    /**
     * @return Earliest finish time of task
     */
    public double getEarliestFinishTime(Task task) {
        return earliestFinishTimes.get(task);
    }

    /**
     * @return Length of critical path
     */
    public double getCriticalPathLength() {
        double len = 0.0;
        for (double eft : earliestFinishTimes.values()) {
            if (eft > len)
                len = eft;
        }
        return len;
    }
}
