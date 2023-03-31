import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class SimClient {
	// Information used by LRR scheduler
	static private List<Server> largestServers;
	static int nextServer = 0;

	public static void main(String[] args) {
		try {
			// Connect to ds-server
			Sim sim = new Sim("localhost", 50000, System.getProperty("user.name"));

			String largest = sim.servers.get(0).getType();
			int largestCores = sim.servers.get(0).getTotalResources().cores;

			// Find the largest server type by core count
			for (Server s : sim.servers) {
				if (s.getTotalResources().cores > largestCores) {
					largest = s.getType();
					largestCores = s.getTotalResources().cores;
				}
			}

			// Get list of largest servers
			largestServers = new ArrayList<Server>();
			for (Server s : sim.servers) {
				if (s.getType().equals(largest))
					largestServers.add(s);
			}

			// Main loop
			loop: while (true) {
				String[] cmd = sim.ready();
				switch (cmd[0]) {
					case "NONE": // We're done, finish up and exit
						break loop;
					case "JOBN": // New job
					case "JOBP":
						Job job = sim.parseJob(cmd);
						scheduleJob(sim, job);
						break;
					case "JCPL": // Job completed
						sim.completeJob(cmd);
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
			sim.close();
		} catch (IOException e) {
			System.out.println("[ERROR] IO: " + e.getMessage());
		}
	}

	// First Free scheduler
	/*static void scheduleJob(Sim sim, Job job) throws IOException {
		boolean scheduled = false;

		// Find the smallest server and use that if possible
		for (Server s : sim.servers) {
			if (s.canFitJobNow(job)) {
				sim.scheduleJob(s, job);
				scheduled = true;
				break;
			}
		}

		// We ran out of free servers, find the server with the least jobs and use it
		if (!scheduled) {
			Server best = sim.servers.get(sim.servers.size() - 1); // Pick a server that we know will work
			for (Server s : sim.servers) {
				if (best.jobCount() > s.jobCount() && s.canFitJob(job)) {
					best = s;
				}
			}

			sim.scheduleJob(best, job);
		}
	}*/

	// First Capable scheduler
	/*static void scheduleJob(Sim sim, Job job) throws IOException {
		for (Server s : sim.servers) {
			if (s.canFitJob(job)) {
				sim.scheduleJob(s, job);
				break;
			}
		}
	}*/

	// Largest Round Robin scheduler
	static void scheduleJob(Sim sim, Job job) throws IOException {
		sim.scheduleJob(largestServers.get(nextServer), job);
		nextServer++;
		if (nextServer >= largestServers.size())
			nextServer = 0;
	}
}

// ds-sim helper class
class Sim {
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;

	public List<Server> servers = new ArrayList<Server>();
	public List<Job> jobs = new ArrayList<Job>();

	private String queueCommand[] = null; // used if we want to do something before scheduling a job

	/*
	 * Connection
	 */

	// Connect to server, setup buffers, and log in
	Sim(String host, int port, String username) throws IOException {
		socket = new Socket(InetAddress.getByName(host), port);
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		sendReceive("HELO");
		sendReceive("AUTH " + username);
		queueCommand = ready();

		getServers();
	}

	// Log out, close buffers and disconnect from server
	public void close() throws IOException {
		sendReceive("QUIT");

		out.close();
		in.close();
		socket.close();
	}

	/*
	 * Preparation
	 */

	// Send - REDY
	// Asks for next simulation step
	public String[] ready() throws IOException {
		if (queueCommand != null) {
			String[] ret = queueCommand;
			queueCommand = null;
			return ret;
		}

		return sendReceive("REDY").split(" ");
	}

	/*
	 * Simulation
	 */

	// Receive - JOBN, JOBP
	// Parse a new job command and add it to the local store
	public Job parseJob(String[] args) {
		Job job = new Job(
			Integer.parseInt(args[2]),
			Integer.parseInt(args[1]),
			new Resources(
				Integer.parseInt(args[4]),
				Integer.parseInt(args[5]),
				Integer.parseInt(args[6])
			)
		);
		jobs.add(job.id, job);
		return job;
	}

	// Receive - JCPL
	// Remove a completed job from the store
	public Job completeJob(String[] args) {
		//System.out.println("[INFO] Job " + args[2] + " running on server " + args[3] + " " + args[4] + " completed at " + args[1] + ".");
		Job job = jobs.get(Integer.parseInt(args[2]));
		job.server.removeJob(job);
		// TODO: Find a better way of doing this, probably with a statically allocated array, as it is this is essentially a memory leak
		jobs.set(job.id, null);
		return job;
	}

	/*
	 * Client
	 */

	// Send - GETS
	// Get a list of servers and add them to the local store
	private void getServers() throws IOException {
		String[] serverData = receiveData(sendReceiveCmd("GETS All"));
		servers.clear();
		for (int i = 0; i < serverData.length; i++) {
			String[] data = serverData[i].split(" ");
			servers.add(new Server(
				data[0],
				Integer.parseInt(data[1]),
				new Resources(
					Integer.parseInt(data[4]),
					Integer.parseInt(data[5]),
					Integer.parseInt(data[6])
				),
				data[2]
			));
		}

		// TODO: Sort server list by size if they aren't already
	}

	// Send - SCHD
	// Schedule a stored job
	public void scheduleJob(Server server, Job job) throws IOException {
		sendReceive("SCHD " + job.id + " " + server.getName());
		server.addJob(job);
		//System.out.println("[INFO] Scheduled job " + job.id + " on server " + server.getName() + ".");
	}

	/*
	 * Helper Functions
	 */

	// Send message to server
	private void send(String message) throws IOException {
		//System.out.println("[DEBUG] Send:    " + message);
		out.println(message);
	}

	// Get response from server
	private String receive() throws IOException {
		String message = in.readLine();
		//System.out.println("[DEBUG] Receive: " + message);
		return message;
	}

	// Send message and get response
	private String sendReceive(String message) throws IOException {
		send(message);
		return receive();
	}

	// Send message and get a response in an array
	private String[] sendReceiveCmd(String message) throws IOException {
		return sendReceive(message).split(" ");
	}

	// Get bulk data from server
	private String[] receiveData(String[] args) throws IOException {
		if (!args[0].equals("DATA")) {
			return null;
		}
		send("OK");

		int lines = Integer.parseInt(args[1]);
		String[] ret = new String[lines];
		for (int i = 0; i < lines; i++) {
			ret[i] = receive();
		}

		sendReceive("OK");
		return ret;
	}
}

// Server local data store
class Server {
	private String type;
	private int id;
	private Resources resources;
	private Resources free;
	private List<Job> jobs = new ArrayList<Job>();

	public String status;

	public String getName() {
		return type + " " + id;
	}

	public String getType() {
		return type;
	}

	public boolean canFitJob(Job job) {
		return resources.greaterThan(job.requirements);
	}

	public boolean canFitJobNow(Job job) {
		return free.greaterThan(job.requirements);
	}

	public void addJob(Job job) {
		jobs.add(job);
		job.addServer(this);
		calcResources();
	}

	public void removeJob(Job job) {
		jobs.remove(job);
		job.removeServer();
		calcResources();
	}

	public int jobCount() {
		return jobs.size();
	}

	private void calcResources() {
		free = new Resources(resources);
		for (Job j : jobs) {
			free.subtract(j.requirements);
		}
	}

	public Resources getTotalResources() {
		return resources;
	}

	public Resources getFreeResources() {
		return free;
	}

	Server(String t, int i, Resources r, String s) {
		type = t;
		id = i;
		resources = r;
		free = r;
		status = s;

		//System.out.println("[INFO] Server " + getName() + " has " + r.cores + " cores, " + r.memory + " memory, and " + r.disk + " disk.");
	}
}

// Job local data store
class Job {
	public int id;
	public int time;
	public Resources requirements;
	public Server server = null;

	public void addServer(Server s) {
		if (server != null)
			return;

		server = s;
	}

	public void removeServer() {
		server = null;
	}

	Job(int i, int t, Resources r) {
		id = i;
		time = t;
		requirements = r;
	}
}

// Resource capability or requirements
class Resources {
	public int cores;
	public int memory;
	public int disk;

	public boolean greaterThan(Resources r) {
		return (
			cores > r.cores &&
			memory > r.memory &&
			disk > r.disk
		);
	}

	public void subtract(Resources r) {
		cores -= r.cores;
		memory -= r.memory;
		disk -= r.disk;
	}

	Resources(int c, int m, int d) {
		cores = c;
		memory = m;
		disk = d;
	}

	Resources(Resources r) {
		cores = r.cores;
		memory = r.memory;
		disk = r.disk;
	}
}
