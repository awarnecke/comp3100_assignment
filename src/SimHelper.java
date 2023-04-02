import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

// ds-sim helper class
public class SimHelper {
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
	SimHelper(String host, int port, String username) throws IOException {
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
