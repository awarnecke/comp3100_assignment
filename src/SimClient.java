import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimClient {
	// Information used by LRR scheduler
	static private List<Server> largestServers;
	static int nextServer = 0;

	public static void main(String[] args) {
		try {
			// Connect to ds-server
			SimHelper helper = new SimHelper("localhost", 50000, System.getProperty("user.name"));

			// Choose scheduler
			Scheduler scheduler = SimClient::scheduler_ff;
			for (int i = 1; i < args.length; i++) {
				if (args[i - 1].equals("-s")) {
					switch (args[i]) {
						case "ff":
							scheduler = SimClient::scheduler_ff;
							break;
						case "fc":
							scheduler = SimClient::scheduler_fc;
							break;
						case "lrr":
							scheduler = SimClient::scheduler_lrr;
							break;
						default:
							System.out.println("[ERROR] Invalid scheduler " + args[i]);
							break;
					}
					break;
				}
			}

			// LRR extra info
			String largest = helper.servers.get(0).getType();
			int largestCores = helper.servers.get(0).getTotalResources().cores;

			// Find the largest server type by core count
			for (Server s : helper.servers) {
				if (s.getTotalResources().cores > largestCores) {
					largest = s.getType();
					largestCores = s.getTotalResources().cores;
				}
			}

			// Get list of largest servers
			largestServers = new ArrayList<Server>();
			for (Server s : helper.servers) {
				if (s.getType().equals(largest))
					largestServers.add(s);
			}

			// Main loop
			loop: while (true) {
				String[] cmd = helper.ready();
				switch (cmd[0]) {
					case "NONE": // We're done, finish up and exit
						break loop;
					case "JOBN": // New job
					case "JOBP":
						Job job = helper.parseJob(cmd);
						scheduler.schedule(helper, job);
						break;
					case "JCPL": // Job completed
						helper.completeJob(cmd);
						break;
					case "ERR": // Something went wrong
						System.out.println("[ERROR] Unexpected error");
						break loop;
					default: // Unhandled command, error out
						System.out.println("[ERROR] Unexpected command " + cmd[0]);
						break;
				}
			}

			// Disconnect from the server and clean up
			helper.close();
		} catch (IOException e) {
			System.out.println("[ERROR] IO: " + e.getMessage());
		}
	}

	interface Scheduler {
		void schedule(SimHelper helper, Job job) throws IOException;
	}

	// First Fit scheduler
	static void scheduler_ff(SimHelper helper, Job job) throws IOException {
		// Find the smalest server that can fit the job now and use it
		for (Server s : helper.servers) {
			if (s.canFitJobNow(job)) {
				helper.scheduleJob(s, job);
				return;
			}
		}
		// We ran out of servers, find the smallest server that can fit the job ever and use it instead
		for (Server s : helper.servers) {
			if (s.canFitJob(job)) {
				helper.scheduleJob(s, job);
				return;
			}
		}
	}

	// First Capable scheduler
	static void scheduler_fc(SimHelper helper, Job job) throws IOException {
		// Find the smallest server that can run the job and use it
		for (Server s : helper.servers) {
			if (s.canFitJob(job)) {
				helper.scheduleJob(s, job);
				break;
			}
		}
	}

	// Largest Round Robin scheduler
	static void scheduler_lrr(SimHelper helper, Job job) throws IOException {
		// Schedule job on the previously saved "next server" and then increment
		helper.scheduleJob(largestServers.get(nextServer), job);
		nextServer++;
		if (nextServer >= largestServers.size())
			nextServer = 0;
	}
}
