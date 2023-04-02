// Job local data store
public class Job {
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
