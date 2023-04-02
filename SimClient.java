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
						scheduleJob(helper, job);
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

	// First Free scheduler
	/*static void scheduleJob(SimHelper helper, Job job) throws IOException {
		boolean scheduled = false;

		// Find the smallest server and use that if possible
		for (Server s : helper.servers) {
			if (s.canFitJobNow(job)) {
				helper.scheduleJob(s, job);
				scheduled = true;
				break;
			}
		}

		// We ran out of free servers, find the server with the least jobs and use it
		if (!scheduled) {
			Server best = helper.servers.get(helper.servers.size() - 1); // Pick a server that we know will work
			for (Server s : helper.servers) {
				if (best.jobCount() > s.jobCount() && s.canFitJob(job)) {
					best = s;
				}
			}

			helper.scheduleJob(best, job);
		}
	}*/

	// First Capable scheduler
	/*static void scheduleJob(SimHelper helper, Job job) throws IOException {
		for (Server s : helper.servers) {
			if (s.canFitJob(job)) {
				helper.scheduleJob(s, job);
				break;
			}
		}
	}*/

	// Largest Round Robin scheduler
	static void scheduleJob(SimHelper helper, Job job) throws IOException {
		helper.scheduleJob(largestServers.get(nextServer), job);
		nextServer++;
		if (nextServer >= largestServers.size())
			nextServer = 0;
	}
}
