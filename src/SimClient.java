import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimClient {
	// Information used by LRR scheduler
	static private List<Server> largestServers;
	static int nextServer = 0;

	// Help message
	// TODO: automatically create a list of schedulers
	static final String helpmsg = """
			Sim Client Usage:
			  java SimClient (-h / --help)
			  java SimClient (-s / --scheduler) [scheduler]
			  java SimClient

			Available Schedulers:
			  fff (default)
			  ff
			  fc
			  bf
			  wf
			  lrr
			""";

	public static void main(String[] args) {
		// If help is requested then show that and exit
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-h") || args[i].equals("--help")) {
				System.out.print(helpmsg);
				return;
			}
		}

		try {
			// Connect to ds-server
			SimHelper helper = new SimHelper("localhost", 50000, System.getProperty("user.name"));

			// Choose scheduler, default is fff
			Scheduler scheduler = SimClient::scheduler_fff;
			for (int i = 1; i < args.length; i++) {
				if (args[i - 1].equals("-s") || args[i - 1].equals("--scheduler")) {
					switch (args[i]) {
						case "fff":
							scheduler = SimClient::scheduler_fff;
							break;
						case "ff":
							scheduler = SimClient::scheduler_ff;
							break;
						case "fc":
							scheduler = SimClient::scheduler_fc;
							break;
						case "bf":
							scheduler = SimClient::scheduler_bf;
							break;
						case "wf":
							scheduler = SimClient::scheduler_wf;
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

	// Fair First Fit scheduler
	static void scheduler_fff(SimHelper helper, Job job) throws IOException {
		// Find the first server that can fit the job
		for (Server s : helper.servers) {
			if (s.canFitJobNow(job)) {
				helper.scheduleJob(s, job);
				return;
			}
		}
		// We ran out of servers, find the server with the shortest queue relative to its size and use it instead
		Server best = helper.servers.get(0);
		float bestrqd = (float)best.getUsedResources().cores / (float)best.getTotalResources().cores;
		for (Server s : helper.servers) {
			if (s.canFitJob(job)) {
				float rqd = (float)s.getUsedResources().cores / (float)s.getTotalResources().cores;
				if (rqd < bestrqd) {
					bestrqd = rqd;
					best = s;
				}
			}
		}
		helper.scheduleJob(best, job);
	}

	// First Fit scheduler
	static void scheduler_ff(SimHelper helper, Job job) throws IOException {
		// Find the smallest server that can fit the job now and use it
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

	// Best Fit scheduler
	static void scheduler_bf(SimHelper helper, Job job) throws IOException {
		// Find the smallest server that can fit the job now
		Server bf = null;
		for (Server s : helper.servers) {
			if (s.canFitJobNow(job) && (bf == null || s.getFreeResources().cores < bf.getFreeResources().cores)) {
				bf = s;
			}
		}
		if (bf != null) {
			helper.scheduleJob(bf, job);
			return;
		}
		// If nothing can fit the job now find the smallest server that can fit the job
		for (Server s : helper.servers) {
			if (s.canFitJob(job)) {
				helper.scheduleJob(s, job);
				return;
			}
		}
	}

	// Worst Fit Scheduler
	static void scheduler_wf(SimHelper helper, Job job) throws IOException {
		// Find the worst fit server
		Server wf = null;
		for (Server s : helper.servers) {
			if (s.canFitJobNow(job) && (wf == null || s.getFreeResources().cores > wf.getFreeResources().cores)) {
				wf = s;
			}
		}
		if (wf != null) {
			helper.scheduleJob(wf, job);
			return;
		}
		// If nothing can fit the job now find the largest server that can fit the job
		for (Server s : helper.servers) {
			if (s.canFitJob(job) && (wf == null || s.getTotalResources().cores > wf.getTotalResources().cores)) {
				wf = s;
			}
		}
		helper.scheduleJob(wf, job);
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
