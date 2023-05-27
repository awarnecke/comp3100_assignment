import java.util.ArrayList;
import java.util.List;

// Server local data store
public class Server {
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
		return resources.greaterThanEqual(job.requirements);
	}

	public boolean canFitJobNow(Job job) {
		return free.greaterThanEqual(job.requirements);
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

	public Resources getUsedResources() {
		Resources used = new Resources();
		for (Job j : jobs) {
			used.add(j.requirements);
		}
		return used;
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
