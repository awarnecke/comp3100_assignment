// Resource capability or requirements
public class Resources {
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
